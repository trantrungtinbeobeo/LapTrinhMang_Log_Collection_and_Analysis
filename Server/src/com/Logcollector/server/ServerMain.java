package com.Logcollector.server;

import com.Logcollector.server.Analyzer.AnomalyDetector;
import com.Logcollector.server.Core.IngestionEngine;
import com.Logcollector.server.Core.LogQueue;
import com.Logcollector.server.Parser.LogEvent;
import com.Logcollector.server.Parser.LogParser;
import com.Logcollector.server.RealTime.WebSocketServer;
import com.Logcollector.server.Storage.DatabaseManager;
import com.Logcollector.server.UI.ChartManager;
import com.Logcollector.server.UI.DashboardUI;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerMain {
    private static final int UDP_PORT = 514;
    private static final int TCP_PORT = 9999;
    private static final int WS_PORT = 8085;
    private static final int HTTP_PORT = 8080;
    private static final Pattern SSH_IP_PATTERN = Pattern.compile("(?:from|rhost=|\\[)\\s*([0-9]{1,3}(?:\\.[0-9]{1,3}){3})");

    public static final java.util.Set<String> BLOCKED_IPS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static WebSocketServer globalWsServer;
    private static AnomalyDetector globalDetector;

    public static int blockIp(String ip, String reason) {
        if (ip == null || ip.trim().isEmpty() || "127.0.0.1".equals(ip.trim())) return 0;
        String cleanIp = ip.trim();
        BLOCKED_IPS.add(cleanIp);
        System.err.println("\n[FIREWALL BLACKLIST] DA KHOA IP: " + cleanIp + " | Ly do: " + reason);
        int purged = com.Logcollector.server.Storage.DatabaseManager.purgeLogsByIp(cleanIp);
        if (globalWsServer != null) {
            String blockJson = String.format(
                "{\"type\":\"IP_BLOCKED\",\"ip\":\"%s\",\"reason\":\"%s\",\"purged\":%d,\"timestamp\":\"%s\"}",
                escapeJson(cleanIp),
                escapeJson(reason),
                purged,
                Instant.now().toString()
            );
            globalWsServer.broadcast(blockJson);
        }
        return purged;
    }

    public static boolean unblockIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) return false;
        String cleanIp = ip.trim();
        boolean removed = BLOCKED_IPS.remove(cleanIp);
        if (removed) {
            if (globalDetector != null) {
                globalDetector.resetIp(cleanIp);
            }
            System.out.println("\n[FIREWALL UNBLOCK] DA MO KHOA / BO CHAN IP: " + cleanIp);
            if (globalWsServer != null) {
                String unblockJson = String.format(
                    "{\"type\":\"IP_UNBLOCKED\",\"ip\":\"%s\",\"message\":\"Da mo khoa / bo chan IP %s\",\"timestamp\":\"%s\"}",
                    escapeJson(cleanIp),
                    escapeJson(cleanIp),
                    Instant.now().toString()
                );
                globalWsServer.broadcast(unblockJson);
            }
        }
        return removed;
    }

    public static int unblockAllIps() {
        int count = BLOCKED_IPS.size();
        BLOCKED_IPS.clear();
        if (globalDetector != null) {
            globalDetector.resetAll();
        }
        System.out.println("\n[FIREWALL UNBLOCK ALL] DA GO TOAN BO " + count + " IP KHOI BLACKLIST!");
        if (globalWsServer != null) {
            String unblockAllJson = String.format(
                "{\"type\":\"ALL_IPS_UNBLOCKED\",\"count\":%d,\"ip\":\"ALL\",\"message\":\"Da go toan bo %d IP khoi Blacklist\",\"timestamp\":\"%s\"}",
                count,
                count,
                Instant.now().toString()
            );
            globalWsServer.broadcast(unblockAllJson);
        }
        return count;
    }

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   KHOI DONG SERVER TIEP NHAN & PHAN TICH LOG     ");
        System.out.println("==================================================");

        try {
            // 0. Khoi tao co so du lieu SQLite (system_logs)
            System.out.println("[DATABASE] Khoi tao co so du lieu SQLite (system_logs)...");
            DatabaseManager.initializeDatabase();

            // 1. Khoi dong Web Server HTTP (cong 8080) phuc vu Dashboard va WebSocket Server (cong 8085)
            System.out.println("[UI] Dang khoi dong Web Server Dashboard tai cong " + HTTP_PORT + "...");
            DashboardUI dashboardUI = new DashboardUI(HTTP_PORT);
            dashboardUI.start();

            System.out.println("[WEBSOCKET] Dang khoi dong WebSocket Server tai cong " + WS_PORT + "...");
            WebSocketServer wsServer = new WebSocketServer(WS_PORT);
            globalWsServer = wsServer;
            wsServer.start();

            // 2. Khoi tao Bo phat hien bat thuong (AnomalyDetector) va Bo tong hop bieu do (ChartManager)
            ChartManager chartManager = new ChartManager();
            AnomalyDetector detector = new AnomalyDetector();
            globalDetector = detector;
            detector.setAlertListener((alertType, sourceIp, alertMessage) -> {
                System.err.println("\n[CANH BAO AN NINH] [" + alertType + "] IP: " + sourceIp + " -> " + alertMessage);
                String alertJson = String.format(
                    "{\"type\":\"ALERT\",\"alertType\":\"%s\",\"ip\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                    escapeJson(alertType),
                    escapeJson(sourceIp),
                    escapeJson(alertMessage),
                    Instant.now().toString()
                );
                wsServer.broadcast(alertJson);

                // Tu dong chan IP neu dat cap do CRITICAL (Brute-force hoac Port Scan)
                if (alertType != null && alertType.startsWith("CRITICAL")) {
                    blockIp(sourceIp, "Tu dong khoa do dat nguong nguy hiem CRITICAL");
                }
            });

            // 3. Luong tieu thu log tu LogQueue, boc tach qua LogParser, thong ke va phan tich
            ExecutorService consumer = Executors.newSingleThreadExecutor();
            consumer.submit(() -> {
                System.out.println("[PIPELINE] San sang lay log tu Queue, boc tach, luu CSDL va phat tan...");
                int logIndex = 0;
                List<LogEvent> dbBatch = new ArrayList<>();
                long lastDbFlushTime = System.currentTimeMillis();

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // Cho toi da 200ms de lay log; neu khong co log thi kiem tra moc thoi gian 1 giay de flush DB
                        String rawLog = LogQueue.poll(200, TimeUnit.MILLISECONDS);
                        long now = System.currentTimeMillis();

                        if (rawLog != null) {
                            logIndex++;

                            // Boc tach va chuan hoa log
                            LogEvent event = LogParser.parse(rawLog);

                            if (event != null) {
                                String effectiveIp = extractEffectiveIp(rawLog, event.getIp());

                                // Kiem tra Firewall Blacklist: Neu IP da bi chan thi DROP ngay lap tuc
                                if (BLOCKED_IPS.contains(effectiveIp)) {
                                    System.out.println("[FIREWALL DROP] Tu choi goi tin tu IP da bi khoa: " + effectiveIp);
                                    continue;
                                }

                                // Dong bo IP hieu dung vao doi tuong LogEvent truoc khi luu CSDL
                                event.setIp(effectiveIp);

                                // Ghi nhan vao ChartManager phuc vu thong ke bieu do thoi gian thuc
                                chartManager.recordLog(event.getSeverityLevel(), effectiveIp);

                                System.out.println("\n[SERVER NHAN LOG #" + logIndex + "]: " + rawLog);
                                System.out.println("   -> [PARSED] IP: " + effectiveIp
                                        + " | Thiet bi: " + event.getDevice()
                                        + " | Phan loai: [" + event.getCategory() + "]"
                                        + " | Level: " + event.getSeverityLevel()
                                        + " | Noi dung: " + event.getMessage());

                                // Kiem tra dau hieu bat thuong (Brute-force login / Port scan)
                                checkAnomaly(detector, event, rawLog, effectiveIp);

                                // Phat tan log dang JSON qua WebSocket toi Dashboard
                                String logJson = String.format(
                                    "{\"type\":\"LOG\",\"index\":%d,\"ip\":\"%s\",\"device\":\"%s\",\"category\":\"%s\",\"timestamp\":\"%s\",\"level\":\"%s\",\"message\":\"%s\",\"raw\":\"%s\"}",
                                    logIndex,
                                    escapeJson(effectiveIp),
                                    escapeJson(event.getDevice()),
                                    escapeJson(event.getCategory()),
                                    escapeJson(event.getTimestamp()),
                                    escapeJson(event.getSeverityLevel()),
                                    escapeJson(event.getMessage()),
                                    escapeJson(rawLog)
                                );
                                wsServer.broadcast(logJson);

                                // Dua log hop le vao lo cho ghi xuong CSDL
                                dbBatch.add(event);
                            }
                        }

                        // Kiem tra dieu kien ghi Batch Insert xuong CSDL SQLite:
                        // 1. Gom du 50 log
                        // 2. HOAC da troi qua it nhat 1 giay (1000ms) ke tu lan ghi truoc va dang co log cho
                        if (dbBatch.size() >= 50 || (!dbBatch.isEmpty() && (now - lastDbFlushTime >= 1000))) {
                            DatabaseManager.insertLogBatch(new ArrayList<>(dbBatch));
                            dbBatch.clear();
                            lastDbFlushTime = now;
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("[PIPELINE ERROR] " + e.getMessage());
                    }
                }

                // Ghi vet vet toan bo log con sot lai trong lo khi dung chuong trinh
                if (!dbBatch.isEmpty()) {
                    DatabaseManager.insertLogBatch(new ArrayList<>(dbBatch));
                    dbBatch.clear();
                }
            });

            // 4. Khoi dong IngestionEngine (lang nghe UDP va TCP)
            System.out.println("[CORE] Dang mo cong UDP " + UDP_PORT + " va TCP " + TCP_PORT + "...");
            IngestionEngine engine = new IngestionEngine(UDP_PORT, TCP_PORT);
            engine.start();

            String lanIp = DashboardUI.getLocalLanIp();
            System.out.println("==================================================");
            System.out.println("   SERVER DANG CHAY! SAN SANG PHUC VU MANG LAN    ");
            System.out.println("   - Web Dashboard (Localhost): http://localhost:" + HTTP_PORT + "/dashboard.html");
            System.out.println("   - Web Dashboard (Mang LAN) : http://" + lanIp + ":" + HTTP_PORT + "/dashboard.html");
            System.out.println("   - Trang dang nhap test LAN : http://" + lanIp + ":" + HTTP_PORT + "/login.html");
            System.out.println("   - UDP Ingestion (Syslog)   : Cong " + UDP_PORT + " (0.0.0.0:" + UDP_PORT + ")");
            System.out.println("   - TCP Ingestion            : Cong " + TCP_PORT + " (0.0.0.0:" + TCP_PORT + ")");
            System.out.println("   - WebSocket Stream         : Cong " + WS_PORT + " (ws://" + lanIp + ":" + WS_PORT + ")");
            System.out.println("==================================================");

            // Shutdown hook de don dep tai nguyen khi dung chuong trinh
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Dang dung cac tien trinh Server...");
                engine.stop();
                wsServer.stop();
                dashboardUI.stop();
                consumer.shutdownNow();
            }));

            // Giu cho tien trinh chinh chay lien tuc
            Thread.currentThread().join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extractEffectiveIp(String rawLog, String parsedIp) {
        if (parsedIp != null && !parsedIp.isEmpty() && !"127.0.0.1".equals(parsedIp) && !"localhost".equalsIgnoreCase(parsedIp)) {
            return parsedIp;
        }
        if (rawLog != null) {
            Matcher matcher = SSH_IP_PATTERN.matcher(rawLog);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return parsedIp != null ? parsedIp : "127.0.0.1";
    }

    private static void checkAnomaly(AnomalyDetector detector, LogEvent event, String rawLog, String effectiveIp) {
        String content = (event.getMessage() + " " + rawLog).toUpperCase();

        if (content.contains("LOGIN_FAILED") || content.contains("FAILED PASSWORD") || content.contains("AUTHENTICATION FAILURE")) {
            detector.processLoginEvent(effectiveIp, false);
        } else if (content.contains("LOGIN_SUCCESS") || content.contains("SUCCESSFULLY LOGGED IN") || content.contains("ACCEPTED PASSWORD")) {
            detector.processLoginEvent(effectiveIp, true);
        }

        if (content.contains("PORT_SCAN") || content.contains("PORT SCAN")) {
            detector.processConnectionEvent(effectiveIp, 80);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}