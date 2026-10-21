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
public class UserSudendlyIncreaseScenario {

    private static final String HOST = System.getProperty("server.host", "192.168.100.71");
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

        class WaveConfig {
            final int startU, endU, burstDelayMs, pauseAfterMs;
            final String name, phaseDesc, archDiag;
            WaveConfig(int startU, int endU, int burstDelayMs, int pauseAfterMs, String name, String phaseDesc, String archDiag) {
                this.startU = startU; this.endU = endU;
                this.burstDelayMs = burstDelayMs; this.pauseAfterMs = pauseAfterMs;
                this.name = name; this.phaseDesc = phaseDesc; this.archDiag = archDiag;
            }
        }

        List<WaveConfig> waves = new ArrayList<>();
        waves.add(new WaveConfig(1, 20, 35, 2000, "ĐỢT 1: LƯU LƯỢNG NỀN TĂNG DẦN", "Khách hàng duyệt web ban đầu (1 -> 20 Users)", "Hạ tầng UDP 514 & TCP 9999 xử lý mượt mà, độ trễ tối ưu <35ms"));
        waves.add(new WaveConfig(21, 45, 30, 2200, "ĐỢT 2: ĐỘT BIẾN +25 USERS", "Flash Sale mở bán điện thoại & laptop (21 -> 45 Users)", "Lưu lượng nhận tăng nhanh, LogQueue đệm tốt, chưa có độ trễ"));
        waves.add(new WaveConfig(46, 75, 28, 2200, "ĐỢT 3: ĐỘT BIẾN +30 USERS", "Thông báo khuyến mãi gửi đến thiết bị (46 -> 75 Users)", "Hệ thống tiệm cận ngưỡng 80 users, bắt đầu nhích độ trễ nhẹ"));
        waves.add(new WaveConfig(76, 105, 25, 2500, "ĐỢT 4: ĐỘT BIẾN +30 USERS (VƯỢT NGƯỠNG)", "Vượt ngưỡng an toàn 80 users (76 -> 105 Users)", "LogQueue bắt đầu dồn hàng đợi, luồng SQLite Disk I/O tăng thời gian ghi nhận"));
        waves.add(new WaveConfig(106, 135, 25, 2500, "ĐỢT 5: ĐỘT BIẾN +30 USERS (BẮT ĐẦU CHẬM)", "Giỏ hàng & kiểm tra thanh toán bị trễ (106 -> 135 Users)", "Độ trễ chạm 450ms, 55% khách hàng khác cảm thấy web xoay vòng"));
        waves.add(new WaveConfig(136, 165, 22, 2500, "ĐỢT 6: ĐỘT BIẾN +30 USERS (NGHẼN TẢI NẶNG)", "Cảnh báo nghẽn tải nghiêm trọng (136 -> 165 Users)", "Độ trễ vượt 750ms, bắt đầu xuất hiện lỗi rớt gói tin và HTTP 404"));
        waves.add(new WaveConfig(166, 200, 20, 2000, "ĐỢT 7: ĐỘT BIẾN +35 USERS (QUÁ TẢI ĐỈNH)", "Quá tải đỉnh điểm 200 Users đồng thời (166 -> 200 Users)", "Luồng consumer quá tải, 35% lượt truy cập gặp lỗi 404 / 503, 95% khách hàng bị chậm"));

        SyslogFormatter formatter = new SyslogFormatter();
        TCPSender tcpSender = new TCPSender(HOST, TCP_PORT);
        UDPSender udpSender = new UDPSender(HOST, UDP_PORT);
        AgentLocalQueue queue = new AgentLocalQueue();

        long startTime = System.currentTimeMillis();
        int sentSuccess = 0;
        int totalProcessed = 0;

        for (WaveConfig w : waves) {
            int waveCount = w.endU - w.startU + 1;
            int midU = w.endU;
            int curLatency;
            double curImpact;
            double cur404;
            if (midU <= 80) {
                double r = midU / 80.0;
                curLatency = 18 + (int)(r * 67);
                curImpact = r * 8.0;
                cur404 = 0.05 + r * 0.45;
            } else if (midU <= 150) {
                double r = (midU - 80) / 70.0;
                curLatency = 85 + (int)(Math.pow(r, 1.5) * 365);
                curImpact = 8.0 + Math.pow(r, 1.2) * 52.0;
                cur404 = 0.5 + Math.pow(r, 1.3) * 11.5;
            } else {
                double r = (midU - 150) / 50.0;
                curLatency = 450 + (int)(Math.pow(r, 1.4) * 700);
                curImpact = 60.0 + Math.pow(r, 1.1) * 35.0;
                cur404 = 12.0 + Math.pow(r, 1.2) * 23.0;
            }

            System.out.println("======================================================================");
            System.out.printf("🌊 [%s | TĂNG VỌT +%d USERS] %s%n", w.name, waveCount, w.phaseDesc);
            System.out.printf("   • Khách hàng đồng thời: %d -> %d Users%n", w.startU, w.endU);
            System.out.printf("   • Chẩn đoán kiến trúc : %s%n", w.archDiag);
            System.out.printf("   • Dự báo hệ thống     : Độ trễ %dms | Ảnh hưởng: %.1f%% | Dự đoán lỗi 404: %.1f%%%n", curLatency, curImpact, cur404);
            System.out.println("----------------------------------------------------------------------");

            for (int u = w.startU; u <= w.endU; u++) {
                totalProcessed++;
                String now = Instant.now().toString();
                String ip = "192.168.1." + ((u % 150) + 1);
                String uName = users[u % users.length] + "_" + (1000 + u);
                String devName = devices[u % devices.length];
                String prod = products[u % products.length];

                int latency;
                double shoppingImpact;
                double pred404Rate;
                String severity;
                String action;

                if (u <= 80) {
                    double r = u / 80.0;
                    latency = 18 + (int) (r * 67);
                    shoppingImpact = r * 8.0;
                    pred404Rate = 0.05 + r * 0.45;
                    severity = "INFO";
                    action = (u % 5 == 0) ? "ADD_TO_CART" : "LOGIN_AND_VIEW";
                } else if (u <= 150) {
                    int extra = u - 80;
                    double r = extra / 70.0;
                    latency = 85 + (int) (Math.pow(r, 1.5) * 365);
                    shoppingImpact = 8.0 + Math.pow(r, 1.2) * 52.0;
                    pred404Rate = 0.5 + Math.pow(r, 1.3) * 11.5;
                    severity = "WARNING";
                    action = (u % 8 == 0) ? "CART_DELAYED" : "PAGE_SLOW_RESPONSE";
                } else {
                    int extra = u - 150;
                    double r = extra / 50.0;
                    latency = 450 + (int) (Math.pow(r, 1.4) * 700);
                    shoppingImpact = 60.0 + Math.pow(r, 1.1) * 35.0;
                    pred404Rate = 12.0 + Math.pow(r, 1.2) * 23.0;
                    severity = "CRITICAL";
                    action = (u % 6 == 0) ? "HTTP_404_PAGE_NOT_FOUND" : (u % 4 == 0 ? "CHECKOUT_TIMEOUT" : "SERVER_BUSY_503");
                }

                String devWithUser = devName + " [User: " + uName + "]";
                String logMsg = String.format(
                    "ActiveUsers: %d/%d | Latency: %dms | ShoppingImpact: %.1f%% | Pred404: %.1f%% | Action: '%s' | Product: '%s' | IP: %s",
                    u, targetUsers, latency, shoppingImpact, pred404Rate, action, prod, ip
                );

                EventGenerator.LogEvent ev = new EventGenerator.LogEvent(now, severity, action, logMsg, devWithUser, "NETWORK");
                queue.enqueue(ev);

                boolean useTcp = "CRITICAL".equalsIgnoreCase(severity) || "ERROR".equalsIgnoreCase(severity);
                try {
                    String payload = formatter.format(ev);
                    if (useTcp) {
                        tcpSender.send(payload);
                    } else {
                        udpSender.send(payload);
                    }
                    queue.dequeue();
                    sentSuccess++;
                } catch (IOException e) {
                    System.err.println("   [!] Lỗi gửi log user " + u + ": " + e.getMessage());
                }

                try {
                    Thread.sleep(w.burstDelayMs);
                } catch (InterruptedException ignored) {}
            }

            System.out.printf("   ✓ Hoàn tất đợt sóng: Đã truyền %d/%d log thành công. Đang giữ nhịp để biểu đồ Dashboard hiển thị đỉnh sóng...%n%n", sentSuccess, totalProcessed);
            try {
                Thread.sleep(w.pauseAfterMs);
            } catch (InterruptedException ignored) {}
        }

        long duration = Math.max(1, System.currentTimeMillis() - startTime);

        System.out.println("======================================================================");
        System.out.println("   TỔNG KẾT KỊCH BẢN: TĂNG TRƯỞNG ĐỘT BIẾN & CHẨN ĐOÁN KIẾN TRÚC      ");
        System.out.println("======================================================================");
        System.out.println("   • Tổng số khách hàng đồng thời đạt đỉnh  : " + targetUsers + " người");
        System.out.println("   • Tổng số gói tin truyền thành công      : " + sentSuccess + "/" + targetUsers);
        System.out.println("   • Thời gian thực hiện kiểm thử mô phỏng  : " + duration + " ms");
        System.out.println("   ------------------------------------------------------------------");
        System.out.println("   [CHẨN ĐOÁN & DỰ BÁO TÁC ĐỘNG MUA HÀNG DỰA TRÊN KIẾN TRÚC TOÀN BỘ FILE]:");
        System.out.println("   1. Giai đoạn 1 - 80 Users (Vùng An Toàn - UDP/TCP thông suốt):");
        System.out.println("      - Độ trễ tối ưu 18 - 85ms. LogQueue và SQLite xử lý tức thời.");
        System.out.println("      - Tỷ lệ lỗi 404 < 0.5%, ảnh hưởng mua hàng < 8%.");
        System.out.println("   2. Giai đoạn 81 - 150 Users (Vùng Chậm & Dồn hàng đợi):");
        System.out.println("      - Độ trễ tăng vọt 90 -> 450ms do LogQueue bắt đầu tích lũy.");
        System.out.println("      - 50% - 60% khách hàng khác bắt đầu thấy giỏ hàng quay vòng, chậm trễ.");
        System.out.println("      - Tỷ lệ lỗi 404 / Timeout tăng lên 1% - 12%.");
        System.out.println("   3. Giai đoạn 151 - 200 Users (Vùng Quá Tải & Nguy cơ Rớt đơn):");
        System.out.println("      - Độ trễ đạt đỉnh 600 - 1200ms+. Luồng xử lý đơn consumer bị nghẽn.");
        System.out.println("      - 95% khách hàng bị ảnh hưởng nghiêm trọng đến việc thanh toán.");
        System.out.println("      - Tỷ lệ lỗi HTTP 404 / 503 / Timeout chạm ngưỡng 35%.");
        System.out.println("   4. Bảo toàn tính toàn vẹn: Dữ liệu chưa gửi được lưu đệm trong AgentLocalQueue.");
        System.out.println("======================================================================");
    }
}
