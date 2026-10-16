package com.Logcollector.agent.demoClientagent;

import com.Logcollector.agent.AgentLocalQueue;
import com.Logcollector.agent.EventGenerator;
import com.Logcollector.agent.Sender.TCPSender;
import com.Logcollector.agent.Sender.UDPSender;
import com.Logcollector.agent.SyslogFormatter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Kich ban 1: Nguoi dung dang nhap co delay 3 giay.
 * Muc dich: Phuc vu demo ngat ket noi mang hoac tat Server de kiem tra
 * kha nang luu tru an toan va co che retry cua AgentLocalQueue (Store-and-Forward).
 */
public class LoginOfflineScenario {

    private static final String HOST = System.getProperty("server.host", "127.0.0.1");
    private static final int TCP_PORT = 9999;
    private static final int UDP_PORT = 514;
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 2000;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   KICH BAN DEMO 1: DANG NHAP & KIEM THU NGAT MANG ");
        System.out.println("==================================================");
        System.out.println("[MO PHONG] Nguoi dung 'user_test' (IP: 192.168.1.105) nhap mat khau va bam 'Dang nhap'...");
        System.out.println("[HUONG DAN DEMO]:");
        System.out.println("   -> Ban co 3 giay de ngat mang (tat Wi-Fi/rut mang) hoac tat ServerMain.");
        System.out.println("   -> Neu mat mang: Log se duoc giu an toan trong AgentLocalQueue.");
        System.out.println("   -> Neu co mang: Log se duoc gui thang toi ServerMain.\n");

        for (int i = 3; i >= 1; i--) {
            System.out.println("   [DEM NGUOC DEMO] Con " + i + " giay...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
        }
        System.out.println("\n[THUC THI] Tien hanh xu ly dang nhap va ghi nhan log...");

        // 1. Sinh su kien dang nhap va rớt mạng
        List<EventGenerator.LogEvent> events = new ArrayList<>();
        String now = Instant.now().toString();
        events.add(new EventGenerator.LogEvent(now, "WARNING", "LOGIN_FAILED",
                "Failed password attempt 1/3 for user 'user_test' | IP: 192.168.1.105"));
        events.add(new EventGenerator.LogEvent(now, "ERROR", "CONNECTION_TIMEOUT",
                "Network connection timed out during authentication for user 'user_test'"));

        // 2. Nap vao hang doi cuc bo
        AgentLocalQueue queue = new AgentLocalQueue();
        for (EventGenerator.LogEvent ev : events) {
            queue.enqueue(ev);
        }
        System.out.println("[LOCAL QUEUE] Da nap " + queue.size() + " log vao hang doi an toan tren may client.\n");

        // 3. Tien hanh truyen tai ve Server
        SyslogFormatter formatter = new SyslogFormatter();
        TCPSender tcpSender = new TCPSender(HOST, TCP_PORT);
        UDPSender udpSender = new UDPSender(HOST, UDP_PORT);

        int logIndex = 1;
        while (!queue.isEmpty()) {
            EventGenerator.LogEvent ev = queue.peek();
            if (ev == null) break;

            boolean useTcp = "ERROR".equalsIgnoreCase(ev.getSeverity())
                    || "CRITICAL".equalsIgnoreCase(ev.getSeverity());
            String protocol = useTcp ? "TCP" : "UDP";
            int port = useTcp ? TCP_PORT : UDP_PORT;

            boolean sent = false;
            int retries = 0;

            while (!sent && retries <= MAX_RETRIES) {
                try {
                    String payload = formatter.format(ev);
                    System.out.println("[LOG #" + logIndex + "] Gui qua " + protocol + " -> " + HOST + ":" + port);
                    System.out.println("   [EVENT] " + ev.getEventType() + " | " + ev.getSeverity() + " | " + ev.getMessage());
                    System.out.println("   [PAYLOAD] " + payload);

                    if (useTcp) {
                        tcpSender.send(payload);
                    } else {
                        udpSender.send(payload);
                    }

                    queue.dequeue();
                    sent = true;
                    System.out.println("   -> [THANH CONG] Da chuyen log toi ServerMain!\n");

                } catch (IOException e) {
                    retries++;
                    System.err.println("   -> [THAT BAI - LAN " + retries + "/" + (MAX_RETRIES + 1)
                            + "] Loi mang: " + e.getMessage());

                    if (retries <= MAX_RETRIES) {
                        System.err.println("   -> [RETRY] Thu lai sau " + (RETRY_DELAY_MS / 1000) + "s...");
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    } else {
                        System.err.println("   -> [DA DUNG THU] Giu log an toan trong AgentLocalQueue!");
                        System.err.println("\n[CHUNG MINH HOAN TAT] Mat ket noi toi Server. "
                                + queue.size() + " log chua gui duoc luu giu an toan trong Queue, khong bi mat mat!");
                        break;
                    }
                }
            }
            logIndex++;
            if (!sent) break;
        }

        System.out.println("==================================================");
        System.out.println("   HOAN TAT KICH BAN 1: So log con trong Queue = " + queue.size());
        System.out.println("==================================================");
    }
}
