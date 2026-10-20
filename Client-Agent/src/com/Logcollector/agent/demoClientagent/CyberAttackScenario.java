package com.Logcollector.agent.demoClientagent;

import com.Logcollector.agent.AgentLocalQueue;
import com.Logcollector.agent.EventGenerator;
import com.Logcollector.agent.Sender.TCPSender;
import com.Logcollector.agent.SyslogFormatter;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Kich ban 4: Mo phong hanh vi tan cong an ninh mang (Brute-force password & Port Scan).
 * Su dung chinh IP that cua may Client dang chay de kiem thu:
 * - Giai doan 1: Do mat khau 5 lan -> Server phat hien va Tich hop Tuong lua Khoa IP that nay.
 * - Giai doan 2: Ban tiep 1 request nua tu cung IP that -> Server FIREWALL DROP ngay lap tuc!
 */
public class CyberAttackScenario {

    private static final String HOST = System.getProperty("server.host", "192.168.100.71");
    private static final int TCP_PORT = 9999;

    public static void main(String[] args) {
        String hackerIp = getLocalMachineRealIp();

        System.out.println("==================================================");
        System.out.println("   KICH BAN DEMO 4: MO PHONG TAN CONG AN NINH     ");
        System.out.println("==================================================");
        System.out.println("   [IP THUC CUA MAY CLIENT NAY]: " + hackerIp);
        System.out.println("   [SERVER LOG TIEP NHAN]      : " + HOST + ":" + TCP_PORT);
        System.out.println("   [TIEN TRINH DEMO 2 GIAI DOAN]:");
        System.out.println("      1. Giai doan 1: Ban 5 lan do mat khau -> Server kich hoat CRITICAL va KHOA IP " + hackerIp);
        System.out.println("      2. Giai doan 2: Ban tiep lan thu 6 tu cung IP " + hackerIp + " -> Server FIREWALL DROP goi tin!");
        System.out.println("==================================================\n");

        List<EventGenerator.LogEvent> events = new ArrayList<>();
        String now = Instant.now().toString();

        // 1. Giai doan 1: 5 lan dang nhap sai lien tiep tu IP that cua may Client
        for (int i = 1; i <= 5; i++) {
            String severity = (i >= 5) ? "CRITICAL" : (i >= 3 ? "WARNING" : "INFO");
            events.add(new EventGenerator.LogEvent(
                    now,
                    severity,
                    "LOGIN_FAILED",
                    String.format("Failed password for user admin from IP %s (Lan %d/5)", hackerIp, i)
            ));
        }

        // 2. Hanh vi quet cong tu cung IP that
        events.add(new EventGenerator.LogEvent(
                now,
                "CRITICAL",
                "PORT_SCAN_SIMULATED",
                String.format("Simulated port scan detected across ports [21, 22, 80, 443, 8080] from IP %s", hackerIp)
        ));

        // 3. Giai doan 2: Thu ban them 1 goi tin do mat khau nua sau khi da bi khoa (Lan 6)
        events.add(new EventGenerator.LogEvent(
                now,
                "CRITICAL",
                "LOGIN_FAILED",
                String.format("Failed password for user admin from IP %s (Lan 6/5 - THU LAI SAU KHI BI TUONG LUA KHOA)", hackerIp)
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
                if (logIndex == 7) {
                    System.out.println("-----------------------------------------------------------------");
                    System.out.println(">>> [GIAI DOAN 2: MINH CHUNG TUONG LUA HOAT DONG]");
                    System.out.println(">>> Server vua khoa IP " + hackerIp + " do vuot nguong an ninh 5 lan sai.");
                    System.out.println(">>> Bay gio ban tiep goi tin LAN 6 cung tu IP " + hackerIp + "...");
                    System.out.println(">>> KY VONG: Server se DROP goi tin nay, khong ghi CSDL va khong tang dem!");
                    System.out.println("-----------------------------------------------------------------");
                    Thread.sleep(1500); // Gian cach 1.5 giay de Server kip xu ly Block IP tren Tuong Lua
                }

                String payload = formatter.format(ev);
                System.out.println("[ATTACK LOG #" + (logIndex++) + "] Gui qua TCP -> " + HOST + ":" + TCP_PORT);
                System.out.println("   [EVENT] " + ev.getEventType() + " | " + ev.getMessage());
                System.out.println("   [PAYLOAD] " + payload);

                tcpSender.send(payload);
                queue.dequeue();

                if (logIndex <= 7) {
                    System.out.println("   -> [DA BAN GOI TIN] Server dang phan tich nguy co...\n");
                } else {
                    System.out.println("   -> [DA BAN GOI TIN LAN 6 THU LAI] ==> HAY QUAN SAT TERMINAL SERVER:");
                    System.out.println("      Server se in: [FIREWALL DROP] Tu choi goi tin tu IP da bi khoa: " + hackerIp + "\n");
                }

                Thread.sleep(350); // Gian cach ngan de Server bat kip chuoi su kien

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
        System.out.println("   1. [CANH BAO AN NINH] [BRUTE_FORCE] tu IP " + hackerIp);
        System.out.println("   2. [FIREWALL BLACKLIST] DA KHOA IP: " + hackerIp);
        System.out.println("   3. [FIREWALL DROP] Tu choi goi tin tu IP da bi khoa: " + hackerIp);
        System.out.println("==================================================");
    }

    /**
     * Lay dia chi IPv4 thuc te cua may Client dang chay trong mang LAN / Wi-Fi
     */
    private static String getLocalMachineRealIp() {
        String sysProp = System.getProperty("client.ip");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (!ip.startsWith("127.")) {
                            return ip;
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "192.168.100.85";
        }
    }
}
