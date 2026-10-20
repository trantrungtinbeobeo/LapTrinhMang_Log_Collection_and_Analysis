
package com.Logcollector.agent;

import com.Logcollector.agent.Sender.TCPSender;
import com.Logcollector.agent.Sender.UDPSender;
import com.Logcollector.agent.demoClientagent.CyberAttackScenario;
import com.Logcollector.agent.demoClientagent.DemoClientAgent;
import com.Logcollector.agent.demoClientagent.ProductClickScenario;
import com.Logcollector.agent.demoClientagent.TrafficSpikeScenario;
import com.Logcollector.agent.demoClientagent.UserSudendlyIncreaseScenario;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ClientAgentMain {

    private static final int DEFAULT_EVENT_COUNT = 10;
    private static final String DEFAULT_HOST = System.getProperty("server.host", "192.168.100.71");
    private static final int DEFAULT_TCP_PORT = 9999;
    private static final int DEFAULT_UDP_PORT = 514;
    private static final long RETRY_DELAY_MS = 2000;
    private static final int MAX_RETRIES = 2;

    public static void main(String[] args) {
        String mode = args.length > 0
                ? args[0].toLowerCase(Locale.ROOT)
                : "auto";

        // Neu chay cac kich ban demo dac thu, chuyen tiep truc tiep toi cac file trong demoClientagent
        switch (mode) {
            case "demo":
                DemoClientAgent.main(args);
                return;
            case "users":
            case "usersuddenly":
            case "user-suddenly":
            case "login":
                UserSudendlyIncreaseScenario.main(args);
                return;
            case "product":
                ProductClickScenario.main(args);
                return;
            case "spike":
            case "traffic-spike":
                TrafficSpikeScenario.main(args);
                return;
            case "attack":
                CyberAttackScenario.main(args);
                return;
        }

        System.out.println("==================================================");
        System.out.println("      KHOI DONG CLIENT AGENT (LOG PRODUCER)       ");
        System.out.println("==================================================");
        System.out.println("[CLIENT AGENT] Che do duoc chon: " + mode.toUpperCase(Locale.ROOT));
        System.out.println("[GOI Y CHE DO]:");
        System.out.println("   • auto [n]    : Tu dong phan phoi UDP (514) & TCP (9999) [Mac dinh]");
        System.out.println("   • users       : Chay UserSudendlyIncreaseScenario (Mo hinh 200 nguoi tang dot bien)");
        System.out.println("   • product     : Chay ProductClickScenario (Demo tuong tac Web san pham)");
        System.out.println("   • spike [n]   : Chay TrafficSpikeScenario (Demo 100-200 request Flash Sale)");
        System.out.println("   • attack      : Chay CyberAttackScenario (Demo Brute-force & Alert)");
        System.out.println("   • demo        : Mo Menu chon nhanh tat ca cac kich ban");
        System.out.println("   • tcp / udp   : Gui rieng le qua tung giao thuc");
        System.out.println("   • syslog/json : Xuat thong diep theo chuan Syslog RFC 5424 / JSON");
        System.out.println("==================================================\n");

        int eventCount = parseEventCount(args);
        EventGenerator generator = new EventGenerator();
        List<EventGenerator.LogEvent> events = generator.generateEvents(eventCount);

        System.out.println("[EVENT GENERATOR] Da chuan bi " + events.size() + " su kien log:");
        for (int i = 0; i < Math.min(events.size(), 10); i++) {
            EventGenerator.LogEvent ev = events.get(i);
            System.out.println("   [" + (i + 1) + "] " + ev.getTimestamp()
                    + " | [" + ev.getSeverity() + "] "
                    + ev.getEventType() + " - " + ev.getMessage());
        }
        if (events.size() > 10) {
            System.out.println("   ... va " + (events.size() - 10) + " su kien nua dang cho xu ly.");
        }

        // Nap vao hang doi noi bo AgentLocalQueue
        AgentLocalQueue queue = new AgentLocalQueue();
        for (EventGenerator.LogEvent event : events) {
            queue.enqueue(event);
        }
        System.out.println("[LOCAL QUEUE] Da nap " + queue.size() + " log vao hang doi cuc bo.\n");

        // Xu ly theo che do truyen tai
        switch (mode) {
            case "json": {
                System.out.println("--- XUAT LOG DINH DANG JSON ---");
                JsonFormatter formatter = new JsonFormatter();
                int idx = 1;
                while (!queue.isEmpty()) {
                    EventGenerator.LogEvent ev = queue.dequeue();
                    System.out.println("[JSON #" + (idx++) + "] " + formatter.format(ev));
                }
                break;
            }

            case "syslog":
            case "rfc5424": {
                System.out.println("--- XUAT LOG DINH DANG SYSLOG (RFC 5424) ---");
                SyslogFormatter formatter = new SyslogFormatter();
                int idx = 1;
                while (!queue.isEmpty()) {
                    EventGenerator.LogEvent ev = queue.dequeue();
                    System.out.println("[RFC5424 #" + (idx++) + "] " + formatter.format(ev));
                }
                break;
            }

            case "rfc3164": {
                System.out.println("--- XUAT LOG DINH DANG SYSLOG (RFC 3164) ---");
                SyslogFormatter formatter = new SyslogFormatter();
                int idx = 1;
                while (!queue.isEmpty()) {
                    EventGenerator.LogEvent ev = queue.dequeue();
                    System.out.println("[RFC3164 #" + (idx++) + "] " + formatter.format3164(ev));
                }
                break;
            }

            case "queue":
                testQueue(queue);
                break;

            case "tcp":
                sendTcp(queue, args);
                break;

            case "udp":
                sendUdp(queue, args);
                break;

            case "login":
            case "product":
            case "spike":
            case "traffic-spike":
            case "attack":
            case "auto":
                sendAutomatically(queue, args);
                break;

            default:
                System.err.println("[LOI] Che do khong hop le: '" + mode + "'");
                System.err.println("[GOI Y] Chay mac dinh hoac truyen: auto, login, product, spike, attack, tcp, udp, syslog, json");
                return;
        }

        System.out.println("\n==================================================");
        System.out.println("            HOAN TAT TIEN TRINH CLIENT            ");
        System.out.println("==================================================");
        System.out.println("[TONG KET] Tong so su kien sinh ra: " + events.size());
        System.out.println("[TONG KET] So log con lai trong Queue: " + queue.size());
        if (queue.isEmpty()) {
            System.out.println("[TRANG THAI] Tat ca log da duoc xu ly / truyen tai thanh cong!");
        } else {
            System.out.println("[TRANG THAI] Con " + queue.size() + " log chua duoc gui (dang duoc luu giu an toan trong Queue).");
        }
        System.out.println("==================================================");
    }

    private static void testQueue(AgentLocalQueue queue) {
        System.out.println("=== KIEM THU HANG DOI NOI BO (QUEUE TEST) ===");
        System.out.println("[QUEUE] Tong so log trong hang doi ban dau: " + queue.size());

        int idx = 1;
        while (!queue.isEmpty()) {
            EventGenerator.LogEvent event = queue.dequeue();
            System.out.println("   [DEQUEUE #" + (idx++) + "] "
                    + event.getTimestamp() + " | "
                    + event.getSeverity() + " | "
                    + event.getEventType() + " | "
                    + event.getMessage());
        }
        System.out.println("[QUEUE] So log con lai sau khi dequeue: " + queue.size());
    }

    private static void sendTcp(
            AgentLocalQueue queue, String[] args) {
        String host = args.length > 2 ? args[2] : DEFAULT_HOST;
        int port = parsePort(args, 3, DEFAULT_TCP_PORT);

        SyslogFormatter formatter = new SyslogFormatter();
        TCPSender sender = new TCPSender(host, port);

        System.out.println("=== CHE DO TCP: GUI TOAN BO LOG TOI " + host + ":" + port + " ===");
        sendQueueWithDetails(queue, host, port, "TCP", event -> sender.send(formatter.format(event)), formatter);
    }

    private static void sendUdp(
            AgentLocalQueue queue, String[] args) {
        String host = args.length > 2 ? args[2] : DEFAULT_HOST;
        int port = parsePort(args, 3, DEFAULT_UDP_PORT);

        SyslogFormatter formatter = new SyslogFormatter();
        UDPSender sender = new UDPSender(host, port);

        System.out.println("=== CHE DO UDP: GUI TOAN BO LOG TOI " + host + ":" + port + " ===");
        sendQueueWithDetails(queue, host, port, "UDP", event -> sender.send(formatter.format(event)), formatter);
    }

    private static void sendAutomatically(
            AgentLocalQueue queue, String[] args) {

        String host = args.length > 2 ? args[2] : DEFAULT_HOST;
        int tcpPort = parsePort(args, 3, DEFAULT_TCP_PORT);
        int udpPort = parsePort(args, 4, DEFAULT_UDP_PORT);

        SyslogFormatter formatter = new SyslogFormatter();
        TCPSender tcpSender = new TCPSender(host, tcpPort);
        UDPSender udpSender = new UDPSender(host, udpPort);

        System.out.println("=== CHE DO AUTO: PHAN PHOI GIAO THUC THEO SEVERITY ===");
        System.out.println("[DIEU PHOI] INFO, WARNING   -> Gui qua UDP (" + host + ":" + udpPort + ")");
        System.out.println("[DIEU PHOI] ERROR, CRITICAL -> Gui qua TCP (" + host + ":" + tcpPort + ")\n");

        int successCount = 0;
        int failCount = 0;
        int logIndex = 1;

        while (!queue.isEmpty()) {
            EventGenerator.LogEvent event = queue.peek();

            if (event == null) {
                break;
            }

            boolean useTcp =
                    "ERROR".equalsIgnoreCase(event.getSeverity())
                    || "CRITICAL".equalsIgnoreCase(event.getSeverity());
            String protocol = useTcp ? "TCP" : "UDP";
            int port = useTcp ? tcpPort : udpPort;

            boolean sent = false;
            int retries = 0;

            while (!sent && retries <= MAX_RETRIES) {
                try {
                    String message = formatter.format(event);

                    System.out.println("[LOG #" + logIndex + "] Dang gui qua " + protocol
                            + " -> " + host + ":" + port);
                    System.out.println("   [EVENT] " + event.getEventType() + " | " + event.getSeverity()
                            + " | " + event.getMessage());
                    System.out.println("   [PAYLOAD] " + message);

                    if (useTcp) {
                        tcpSender.send(message);
                    } else {
                        udpSender.send(message);
                    }

                    // Gui thanh cong -> lay khoi queue
                    queue.dequeue();
                    sent = true;
                    successCount++;

                    System.out.println("   -> [THANH CONG] Da chuyen log toi Server qua " + protocol + "!\n");

                } catch (IOException e) {
                    retries++;
                    System.err.println("   -> [THAT BAI - LAN " + retries + "/" + (MAX_RETRIES + 1)
                            + "] Loi gui " + protocol + ": " + e.getMessage());

                    if (retries <= MAX_RETRIES) {
                        System.err.println("   -> [RETRY] Thu lai sau " + (RETRY_DELAY_MS / 1000) + " giay...");
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            System.err.println("   -> Da dung tien trinh do bi ngat!");
                            return;
                        }
                    } else {
                        System.err.println("   -> [HUY THU] Dat gioi han retry. Giu log trong Queue.");
                        failCount++;
                        // Neu Server TCP khong bat thi dung luong gui de bao toan cac log con lai
                        if (useTcp) {
                            System.err.println("\n[CANH BAO] Server TCP (" + host + ":" + tcpPort
                                    + ") hien khong phan hoi. Dung luong gui de bao toan cac log con lai trong Queue.");
                            System.err.println("[GOI Y] Hay khoi dong ServerMain (chay Server/src/.../ServerMain.java) de mo cong " + tcpPort + " truoc khi test!");
                            return;
                        }
                        break;
                    }
                }
            }
            logIndex++;
        }

        System.out.println("[KET QUA AUTO] Gui thanh cong: " + successCount + " | That bai/Luu Queue: " + failCount);
    }

    private static void sendQueueWithDetails(
            AgentLocalQueue queue, String host, int port, String protocol,
            LogSender action, SyslogFormatter formatter) {

        System.out.println("[QUEUE] So log trong hang doi: " + queue.size() + "\n");
        int logIndex = 1;
        int successCount = 0;

        while (!queue.isEmpty()) {
            EventGenerator.LogEvent event = queue.peek();
            if (event == null) break;

            String payload = formatter.format(event);
            System.out.println("[LOG #" + (logIndex++) + "] Dang gui qua " + protocol + " -> " + host + ":" + port);
            System.out.println("   [EVENT] " + event.getEventType() + " | " + event.getSeverity()
                    + " | " + event.getMessage());
            System.out.println("   [PAYLOAD] " + payload);

            try {
                action.send(event);
                queue.dequeue();
                successCount++;
                System.out.println("   -> [THANH CONG] Da gui log thanh cong qua " + protocol + "!\n");
            } catch (IOException e) {
                System.err.println("   -> [THAT BAI] Loi gui qua " + protocol + ": " + e.getMessage());
                System.err.println("   -> [QUEUE] Log van duoc giu lai trong Queue.");
                if ("TCP".equals(protocol)) {
                    System.err.println("[GOI Y] Hay dam bao ServerMain dang mo cong TCP " + port + "!");
                }
                break;
            }
        }

        System.out.println("[KET QUA " + protocol + "] Gui thanh cong: " + successCount
                + " | Con lai trong Queue: " + queue.size());
    }

    private static int parsePort(
            String[] args, int index, int defaultPort) {

        if (args.length <= index) {
            return defaultPort;
        }

        try {
            int port = Integer.parseInt(args[index]);

            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }

            return port;
        } catch (NumberFormatException e) {
            System.err.println(
                    "Port khong hop le, su dung port mac dinh "
                    + defaultPort);
            return defaultPort;
        }
    }

    private static int parseEventCount(String[] args) {
        if (args == null || args.length < 2) {
            return DEFAULT_EVENT_COUNT;
        }

        try {
            int count = Integer.parseInt(args[1]);

            if (count < 0) {
                System.err.println(
                        "So luong su kien khong duoc am. Dung mac dinh " + DEFAULT_EVENT_COUNT);
                return DEFAULT_EVENT_COUNT;
            }

            return count;
        } catch (NumberFormatException e) {
            System.err.println(
                    "So luong su kien khong hop le. Dung mac dinh " + DEFAULT_EVENT_COUNT);
            return DEFAULT_EVENT_COUNT;
        }
    }

    @FunctionalInterface
    private interface LogSender {
        void send(EventGenerator.LogEvent event) throws IOException;
    }
}
