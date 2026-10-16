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
 * Kich ban 2: Nguoi dung thao tac chon xem san pham, gio hang, thanh toan va gap loi 404.
 * Muc dich: Phuc vu demo web san pham, hien thi ro tung loai hanh vi tren Dashboard Server
 * kem IP nguon va do tre (Latency ms).
 */
public class ProductClickScenario {

    private static final String HOST = "127.0.0.1";
    private static final int TCP_PORT = 9999;
    private static final int UDP_PORT = 514;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   KICH BAN DEMO 2: NGUOI DUNG CHON XEM SAN PHAM  ");
        System.out.println("==================================================");
        System.out.println("[MO PHONG] Nhieu nguoi dung thao tac tren Web san pham...");
        System.out.println("   • INFO, WARNING   -> Chuyen qua UDP (514) nhanh chong");
        System.out.println("   • ERROR (Loi 404) -> Chuyen qua TCP (9999) tin cay\n");

        List<EventGenerator.LogEvent> events = new ArrayList<>();
        String now = Instant.now().toString();

        events.add(new EventGenerator.LogEvent(now, "INFO", "VIEW_DEVICE",
                "Admin 'admin_trungtin' viewed 'May chu Dell PowerEdge R750' | IP: 192.168.1.10 | Latency: 18ms"));
        events.add(new EventGenerator.LogEvent(now, "INFO", "VIEW_DEVICE",
                "Admin 'admin_minhhoa' viewed 'Cisco Catalyst Switch 2960-X' | IP: 192.168.1.25 | Latency: 22ms"));
        events.add(new EventGenerator.LogEvent(now, "INFO", "CONFIG_DEVICE",
                "Admin 'admin_quynhnga' configured 'Router MikroTik CCR2004' | IP: 192.168.1.33 | Latency: 35ms"));
        events.add(new EventGenerator.LogEvent(now, "INFO", "DEPLOY_POLICY",
                "Admin 'admin_quanghai' applied security rule on 'Tuong lua Fortinet 60F' | IP: 192.168.1.40 | Latency: 64ms"));
        events.add(new EventGenerator.LogEvent(now, "ERROR", "HTTP_404",
                "User 'guest_8812' accessed non-existing gateway port '/device/mgmt-port-v2' | IP: 192.168.1.99 | Latency: 12ms"));
        events.add(new EventGenerator.LogEvent(now, "INFO", "VIEW_DEVICE",
                "Admin 'admin_hoanglong' checked status 'Module Quang Cisco SFP+ 10G' | IP: 192.168.1.15 | Latency: 20ms"));

        AgentLocalQueue queue = new AgentLocalQueue();
        for (EventGenerator.LogEvent ev : events) {
            queue.enqueue(ev);
        }
        System.out.println("[LOCAL QUEUE] Da nap " + queue.size() + " log tuong tac san pham vao Queue.\n");

        SyslogFormatter formatter = new SyslogFormatter();
        TCPSender tcpSender = new TCPSender(HOST, TCP_PORT);
        UDPSender udpSender = new UDPSender(HOST, UDP_PORT);

        int logIndex = 1;
        int successCount = 0;

        while (!queue.isEmpty()) {
            EventGenerator.LogEvent ev = queue.peek();
            if (ev == null) break;

            boolean useTcp = "ERROR".equalsIgnoreCase(ev.getSeverity())
                    || "CRITICAL".equalsIgnoreCase(ev.getSeverity());
            String protocol = useTcp ? "TCP" : "UDP";
            int port = useTcp ? TCP_PORT : UDP_PORT;

            try {
                String payload = formatter.format(ev);
                System.out.println("[LOG #" + (logIndex++) + "] Gui qua " + protocol + " -> " + HOST + ":" + port);
                System.out.println("   [EVENT] " + ev.getEventType() + " | " + ev.getSeverity() + " | " + ev.getMessage());
                System.out.println("   [PAYLOAD] " + payload);

                if (useTcp) {
                    tcpSender.send(payload);
                } else {
                    udpSender.send(payload);
                }

                queue.dequeue();
                successCount++;
                System.out.println("   -> [THANH CONG] Da chuyen log toi ServerMain!\n");

                // Giãn cách nhẹ 200ms để quan sát luồng trôi trên Dashboard
                Thread.sleep(200);

            } catch (IOException e) {
                System.err.println("   -> [THAT BAI] Loi gui " + protocol + ": " + e.getMessage());
                System.err.println("   -> [HUONG DAN] Hay dam bao ServerMain dang chay de mo cong UDP " + UDP_PORT + " va TCP " + TCP_PORT + "!");
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("==================================================");
        System.out.println("   HOAN TAT KICH BAN 2: Gui thanh cong = " + successCount + "/" + events.size());
        System.out.println("==================================================");
    }
}
