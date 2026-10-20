package com.Logcollector.agent.demoClientagent;

import com.Logcollector.agent.AgentLocalQueue;
import com.Logcollector.agent.EventGenerator;
import com.Logcollector.agent.Sender.TCPSender;
import com.Logcollector.agent.SyslogFormatter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Kich ban 4: Mo phong hanh vi tan cong an ninh mang (Brute-force password & Port Scan).
 * Muc dich: Kiem thu AnomalyDetector tren ServerMain, kich hoat chuong canh bao mau do
 * va broadcast tin nhan ALERT xuong WebSocket Dashboard.
 */
public class CyberAttackScenario {

    private static final String HOST = System.getProperty("server.host", "192.168.100.71");
    private static final int TCP_PORT = 9999;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   KICH BAN DEMO 4: MO PHONG TAN CONG AN NINH     ");
        System.out.println("==================================================");
        System.out.println("[MO PHONG] Hacker dang thuc hien Brute-force mat khau va Quet cong...");
        System.out.println("[KY VONG] ServerMain se phat hien va kich hoat [CANH BAO AN NINH] ALERT mau do!\n");

        List<EventGenerator.LogEvent> events = new ArrayList<>();
        String now = Instant.now().toString();

        // 1. 5 lan dang nhap sai lien tiep tu cung 1 IP hacker:
        // Phan cap leo thang bao mat: Lan 1-2: INFO, Lan 3-4: WARNING, Lan 5: CRITICAL
        String hackerIp = "192.168.1.200";
        for (int i = 1; i <= 5; i++) {
            String severity = (i >= 5) ? "CRITICAL" : (i >= 3 ? "WARNING" : "INFO");
            events.add(new EventGenerator.LogEvent(
                    now,
                    severity,
                    "LOGIN_FAILED",
                    String.format("Failed password for user admin from IP %s (Lan %d/5)", hackerIp, i)
            ));
        }

        // 2. Hanh vi quet cong
        events.add(new EventGenerator.LogEvent(
                now,
                "CRITICAL",
                "PORT_SCAN_SIMULATED",
                "Simulated port scan detected across ports [21, 22, 80, 443, 8080] from IP 10.0.0.99"
        ));

        AgentLocalQueue queue = new AgentLocalQueue();
        for (EventGenerator.LogEvent ev : events) {
            queue.enqueue(ev);
        }
        System.out.println("[LOCAL QUEUE] Da nap " + queue.size() + " log tan cong vao hang doi cuc bo.\n");

        SyslogFormatter formatter = new SyslogFormatter();
        TCPSender tcpSender = new TCPSender(HOST, TCP_PORT);

        int logIndex = 1;
        while (!queue.isEmpty()) {
            EventGenerator.LogEvent ev = queue.peek();
            if (ev == null) break;

            try {
                String payload = formatter.format(ev);
                System.out.println("[ATTACK LOG #" + (logIndex++) + "] Gui qua TCP -> " + HOST + ":" + TCP_PORT);
                System.out.println("   [EVENT] " + ev.getEventType() + " | " + ev.getMessage());
                System.out.println("   [PAYLOAD] " + payload);

                tcpSender.send(payload);
                queue.dequeue();
                System.out.println("   -> [DA BAN GOI TIN] Server dang phan tich nguy co...\n");

                Thread.sleep(300); // Giãn cách ngắn để Server bắt kịp chuỗi sự kiện

            } catch (IOException e) {
                System.err.println("   -> [LOI TCP]: " + e.getMessage());
                System.err.println("   -> Hay dam bao ServerMain dang mo cong TCP " + TCP_PORT + "!");
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("==================================================");
        System.out.println("   HOAN TAT KICH BAN 4: Quan sat Terminal Server  ");
        System.out.println("   de thay dong: [CANH BAO AN NINH] [BRUTE_FORCE]!");
        System.out.println("==================================================");
    }
}
