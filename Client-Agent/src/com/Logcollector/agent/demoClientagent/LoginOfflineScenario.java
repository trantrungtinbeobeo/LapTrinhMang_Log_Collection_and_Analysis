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
 * Kich ban 1: Tang truong 200 nguoi dung dong thoi (Concurrency Stress Test).
 * Muc tieu:
 * 1. Do luong do tre (Latency) tang dan tu 1 -> 200 nguoi.
 * 2. Du bao muc do anh huong cham / nghen mua hang toi nhung khach hang khac.
 * 3. Du doan ti le phat sinh loi HTTP 404 / 503 / Timeout khi he thong cham nguong chiu tai.
 * 4. Kiem chung co che luu tru an toan cua AgentLocalQueue khi dut ket noi mang.
 */
public class LoginOfflineScenario {

    private static final String HOST = System.getProperty("server.host", "127.0.0.1");
    private static final int TCP_PORT = 9999;
    private static final int UDP_PORT = 514;
    private static final int TARGET_CONCURRENCY = 200;

    public static void main(String[] args) {
        int targetUsers = TARGET_CONCURRENCY;
        if (args.length > 0) {
            try {
                targetUsers = Integer.parseInt(args[0]);
                if (targetUsers <= 0) targetUsers = TARGET_CONCURRENCY;
            } catch (NumberFormatException ignored) {}
        }

        System.out.println("======================================================================");
        System.out.println("   KICH BAN DEMO 1: MO HINH TANG TRUONG 200 NGUOI DUNG DONG THOI      ");
        System.out.println("   & DU BAO ANH HUONG MUA HANG + TI LE PHAT SINH LOI 404 / TIMEOUT   ");
        System.out.println("======================================================================");
        System.out.println("[MO PHONG]: Luong khach hang truy cap web tang vot tu 1 -> " + targetUsers + " nguoi...");
        System.out.println("   • 01 - 80  Users: Tai on dinh  | Do tre < 90ms   | Anh huong mua hang: 0% - 8%   | Ti le 404: 0.1%");
        System.out.println("   • 81 - 150 Users: Bat dau cham | Do tre 90-450ms | Anh huong mua hang: 10% - 60% | Ti le 404: 1% - 13%");
        System.out.println("   • 151 - 200 Users: Nghen tai   | Do tre > 600ms  | Anh huong mua hang: 65% - 95% | Ti le 404: 15% - 35%\n");

        String[] devices = {
            "iPhone 16 Pro (iOS)",
            "Samsung Galaxy S24 (Android)",
            "MacBook Pro M3 (macOS)",
            "Dell XPS 15 (Windows 11)",
            "iPad Pro M4 (iPadOS)"
        };
        String[] users = {
            "trungtin",
            "minhhoa",
            "quanghai",
            "quynhnga",
            "hoanglong"
        };
        String[] products = {
            "iPhone 16 Pro Max",
            "MacBook Pro M3 Max",
            "Tai nghe Sony WH-1000XM5",
            "Chuot Logitech MX Master 3S",
            "Laptop Gaming Titan X15"
        };

        List<EventGenerator.LogEvent> events = new ArrayList<>();

        for (int i = 1; i <= targetUsers; i++) {
            String now = Instant.now().toString();
            String ip = "192.168.1." + ((i % 150) + 1);
            String uName = users[i % users.length] + "_" + (1000 + i);
            String devName = devices[i % devices.length];
            String prod = products[i % products.length];

            // 1. Tinh toan do tre thuc te theo luong nguoi tang dan
            int latency;
            double shoppingImpact; // Phan tram anh huong cham toi khach hang khac (%)
            double pred404Rate;     // Du bao ti le loi 404 / Timeout (%)
            String severity;
            String action;

            if (i <= 80) {
                // Vung an toan
                latency = 18 + (int) (i * 0.9);
                shoppingImpact = (i / 80.0) * 8.0;
                pred404Rate = 0.05 + (i / 80.0) * 0.45;
                severity = "INFO";
                action = (i % 5 == 0) ? "ADD_TO_CART" : "LOGIN_AND_VIEW";
            } else if (i <= 150) {
                // Vung bat dau cham & anh huong
                int extra = i - 80;
                latency = 90 + (int) (Math.pow(extra, 1.45) * 2.2);
                shoppingImpact = 10.0 + (extra / 70.0) * 50.0;
                pred404Rate = 1.0 + (extra / 70.0) * 12.0;
                severity = "WARNING";
                action = (i % 8 == 0) ? "CART_DELAYED" : "PAGE_SLOW_RESPONSE";
            } else {
                // Vung nghen tai nghiem trong (151 - 200)
                int extra = i - 150;
                latency = 450 + (int) (Math.pow(extra, 1.6) * 7.5);
                shoppingImpact = 65.0 + (extra / 50.0) * 30.0;
                pred404Rate = 15.0 + (extra / 50.0) * 20.0;
                severity = "CRITICAL";
                action = (i % 6 == 0) ? "HTTP_404_PAGE_NOT_FOUND" : (i % 4 == 0 ? "CHECKOUT_TIMEOUT" : "SERVER_BUSY_503");
            }

            String devWithUser = devName + " [User: " + uName + "]";
            String logMsg = String.format(
                "ActiveUsers: %d/%d | Latency: %dms | ShoppingImpact: %.1f%% | Pred404: %.1f%% | Action: '%s' | Product: '%s' | IP: %s",
                i, targetUsers, latency, shoppingImpact, pred404Rate, action, prod, ip
            );

            events.add(new EventGenerator.LogEvent(now, severity, action, logMsg, devWithUser, "NETWORK"));
        }

        // 2. Nap vao hang doi cuc bo
        AgentLocalQueue queue = new AgentLocalQueue();
        for (EventGenerator.LogEvent ev : events) {
            queue.enqueue(ev);
        }
        System.out.println("[LOCAL QUEUE] Da nap thanh cong " + queue.size() + " ban ghi luu luong vao hang doi cuc bo.");
        System.out.println("[BAT DAU TRUYEN TAI DULIEU VA THEO DOI TREN DASHBOARD]...\n");

        SyslogFormatter formatter = new SyslogFormatter();
        TCPSender tcpSender = new TCPSender(HOST, TCP_PORT);
        UDPSender udpSender = new UDPSender(HOST, UDP_PORT);

        long startTime = System.currentTimeMillis();
        int sentSuccess = 0;
        int logIndex = 1;

        while (!queue.isEmpty()) {
            EventGenerator.LogEvent ev = queue.peek();
            if (ev == null) break;

            boolean useTcp = "CRITICAL".equalsIgnoreCase(ev.getSeverity())
                    || "ERROR".equalsIgnoreCase(ev.getSeverity());

            try {
                String payload = formatter.format(ev);
                if (useTcp) {
                    tcpSender.send(payload);
                } else {
                    udpSender.send(payload);
                }

                queue.dequeue();
                sentSuccess++;

                // In tien trinh truc quan moi 25 nguoi va moc 50, 100, 150, 200
                if (logIndex == 1 || logIndex % 25 == 0 || logIndex == targetUsers) {
                    int curUsers = logIndex;
                    int curLatency = (curUsers <= 80) ? (18 + (int)(curUsers * 0.9)) : (curUsers <= 150 ? (90 + (int)(Math.pow(curUsers - 80, 1.45) * 2.2)) : (450 + (int)(Math.pow(curUsers - 150, 1.6) * 7.5)));
                    double curImpact = (curUsers <= 80) ? ((curUsers / 80.0) * 8.0) : (curUsers <= 150 ? (10.0 + ((curUsers - 80) / 70.0) * 50.0) : (65.0 + ((curUsers - 150) / 50.0) * 30.0));
                    double cur404 = (curUsers <= 80) ? (0.05 + (curUsers / 80.0) * 0.45) : (curUsers <= 150 ? (1.0 + ((curUsers - 80) / 70.0) * 12.0) : (15.0 + ((curUsers - 150) / 50.0) * 20.0));

                    System.out.printf(
                        "👉 [TIEN TRINH %3d/%3d USERS] %-4s | Do tre: %4dms | Anh huong khach khac: %5.1f%% | Du doan loi 404: %4.1f%%%n",
                        curUsers, targetUsers, (useTcp ? "TCP" : "UDP"), curLatency, curImpact, cur404
                    );
                }

                // Do tre nhe 15ms giua cac log de Windows khong nghen port va bieu do Dashboard hien thi song nuot na
                try {
                    Thread.sleep(15);
                } catch (InterruptedException ignored) {}

            } catch (IOException e) {
                System.err.println("[LOI TRUYEN TAI #" + logIndex + "]: " + e.getMessage());
                if (e instanceof java.net.ConnectException) {
                    System.err.println("   -> [CHU Y]: Khong the ket noi toi ServerMain!");
                    System.err.println("   -> Log chua gui duoc duoc giu an toan trong AgentLocalQueue (" + queue.size() + " log).");
                }
                break;
            }
            logIndex++;
        }

        long duration = Math.max(1, System.currentTimeMillis() - startTime);

        System.out.println("\n======================================================================");
        System.out.println("   TONG KET KICH BAN 1: TANG TRUONG 200 USERS & DU BAO HIEN TRANG    ");
        System.out.println("======================================================================");
        System.out.println("   • So luong khach hang dong thoi mo phong : " + targetUsers + " nguoi");
        System.out.println("   • So luot gui thanh cong ve ServerMain   : " + sentSuccess + "/" + targetUsers);
        System.out.println("   • Thoi gian thuc hien mo phong           : " + duration + " ms");
        System.out.println("   ------------------------------------------------------------------");
        System.out.println("   [KET LUAN DU BAO ANH HUONG KINH DOANH & NGHEN TAI]:");
        System.out.println("   1. Kha nang gay cham: KHI DAT 150-200 NGUOI, do tre tang tu 18ms -> 1150ms+.");
        System.out.println("      -> 85% - 95% khach hang khac se cam thay web bi xoay vong, cham ro ret!");
        System.out.println("   2. Anh huong viec mua hang: Giỏ hàng bi delay, thanh toan gap timeout, ti le bo gio hang cao.");
        System.out.println("   3. Du doan ti le loi 404/Timeout: Uoc tinh 25% - 35% luot truy cap gap loi khi cham nguong 200 nguoi");
        System.out.println("      neu he thong khong co bo dem Queue hoac Scale tai nguyen.");
        System.out.println("   4. Bao toan du lieu: " + queue.size() + " log con sot trong Local Queue neu ngat Server.");
        System.out.println("======================================================================");
    }
}
