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
 * Kich ban 3: Dot bien luu luong (Traffic Spike / Flash Sale 100 - 200 user cung luc).
 * Muc dich: Kiem thu kha nang chiu tai, do luong su gia tang do tre (Latency)
 * va chung minh Server nuot tron luu luong lon ma khong bi sap.
 */
public class TrafficSpikeScenario {

    private static final String HOST = "127.0.0.1";
    private static final int TCP_PORT = 9999;
    private static final int UDP_PORT = 514;
    private static final int DEFAULT_SPIKE_COUNT = 150;

    public static void main(String[] args) {
        int count = DEFAULT_SPIKE_COUNT;
        if (args.length > 0) {
            try {
                count = Integer.parseInt(args[0]);
                if (count <= 0) count = DEFAULT_SPIKE_COUNT;
            } catch (NumberFormatException ignored) {}
        }

        System.out.println("==================================================");
        System.out.println("   KICH BAN DEMO 3: DOT BIEN LUU LUONG (FLASH SALE)");
        System.out.println("==================================================");
        System.out.println("[MO PHONG] Su kien Flash Sale: " + count + " nguoi dung dong loat click xem & mua san pham!");
        System.out.println("[KY VONG] Do tre (Latency) se tang dan do tai dồn dập, Server va Client khong bi nghen/drop.\n");

        List<EventGenerator.LogEvent> events = new ArrayList<>();
        String[] devices = {"May chu Dell R750", "Cisco Switch 2960", "Router MikroTik 2004", "Tuong lua Fortinet 60F", "Module SFP+ 10G"};

        for (int i = 1; i <= count; i++) {
            String now = Instant.now().toString();
            String ip = "192.168.1." + ((i % 150) + 1);
            String user = "operator_" + (1000 + i);
            String dev = devices[i % devices.length];

            // Tinh toan do tre tang dan khi luu luong don tai:
            // - Duoi 200ms : INFO (Binh thuong)
            // - 200ms - 400ms: WARNING (Canh bao nghen luu luong)
            // - Tren 400ms  : CRITICAL (Nguy hiem - qua tai he thong)
            int latency = 15 + (int) (Math.pow(i, 1.22) * 0.45);
            String severity = (latency > 400) ? "CRITICAL" : (latency > 200 ? "WARNING" : "INFO");
            String eventType = (i % 7 == 0) ? "HEAVY_REPORT" : "TELEMETRY_POLL";

            events.add(new EventGenerator.LogEvent(
                    now,
                    severity,
                    eventType,
                    String.format("Device '%s' polled by '%s' | IP: %s | Latency: %dms", dev, user, ip, latency)
            ));
        }

        AgentLocalQueue queue = new AgentLocalQueue();
        for (EventGenerator.LogEvent ev : events) {
            queue.enqueue(ev);
        }
        System.out.println("[LOCAL QUEUE] Da nap " + queue.size() + " log vao hang doi cuc bo.");
        System.out.println("[BAT DAU BAN GOI TIN DON DAP TOI SERVER]...\n");

        SyslogFormatter formatter = new SyslogFormatter();
        TCPSender tcpSender = new TCPSender(HOST, TCP_PORT);
        UDPSender udpSender = new UDPSender(HOST, UDP_PORT);

        long startTime = System.currentTimeMillis();
        int sentSuccess = 0;
        int logIndex = 1;

        while (!queue.isEmpty()) {
            EventGenerator.LogEvent ev = queue.peek();
            if (ev == null) break;

            boolean useTcp = "ERROR".equalsIgnoreCase(ev.getSeverity())
                    || "CRITICAL".equalsIgnoreCase(ev.getSeverity());

            try {
                String payload = formatter.format(ev);
                if (useTcp) {
                    tcpSender.send(payload);
                } else {
                    udpSender.send(payload);
                }

                queue.dequeue();
                sentSuccess++;

                if (logIndex <= 5 || logIndex % 25 == 0 || logIndex == count) {
                    System.out.println("[PROGRESS " + logIndex + "/" + count + "] "
                            + (useTcp ? "TCP" : "UDP") + " -> " + ev.getMessage());
                }

            } catch (IOException e) {
                System.err.println("[LOI TRUYEN TAI #" + logIndex + "]: " + e.getMessage());
                System.err.println("Dung gui de bao toan so log con lai trong Queue: " + queue.size());
                break;
            }
            logIndex++;
        }

        long duration = Math.max(1, System.currentTimeMillis() - startTime);
        double rps = (sentSuccess * 1000.0) / duration;

        System.out.println("\n==================================================");
        System.out.println("   TONG KET KICH BAN 3: TRAFFIC SPIKE HOAN TAT    ");
        System.out.println("==================================================");
        System.out.println("   • Tong so luot click sinh ra  : " + count);
        System.out.println("   • So luot gui thanh cong toi  : " + sentSuccess);
        System.out.println("   • Thoi gian ban het luu luong : " + duration + " ms");
        System.out.printf("   • Toc do truyen tai trung binh: %.1f Requests/giay (RPS)%n", rps);
        System.out.println("   • So log con sot trong Queue  : " + queue.size());
        System.out.println("==================================================");
    }
}
