package com.Logcollector.server;

import com.Logcollector.server.Analyzer.AnomalyDetector;
import com.Logcollector.server.Core.IngestionEngine;
import com.Logcollector.server.Core.LogQueue;
import com.Logcollector.server.Parser.LogEvent;
import com.Logcollector.server.Parser.LogParser;
import com.Logcollector.server.RealTime.WebSocketServer;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerMain {
    private static final int UDP_PORT = 514;
    private static final int TCP_PORT = 9999;
    private static final int WS_PORT = 8085;
    private static final Pattern SSH_IP_PATTERN = Pattern.compile("(?:from|rhost=|\\[)\\s*([0-9]{1,3}(?:\\.[0-9]{1,3}){3})");

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   KHOI DONG SERVER TIEP NHAN & PHAN TICH LOG     ");
        System.out.println("==================================================");

        try {
            // 1. Khoi dong WebSocket Server de broadcast log thoi gian thuc xuong Web Dashboard
            System.out.println("[WEBSOCKET] Dang khoi dong WebSocket Server tai cong " + WS_PORT + "...");
            WebSocketServer wsServer = new WebSocketServer(WS_PORT);
            wsServer.start();

            // 2. Khoi tao Bo phat hien bat thuong (AnomalyDetector) va gan callback day alert qua WebSocket
            AnomalyDetector detector = new AnomalyDetector();
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
            });

            // 3. Luong tieu thu log tu LogQueue, boc tach qua LogParser va phan tich
            ExecutorService consumer = Executors.newSingleThreadExecutor();
            consumer.submit(() -> {
                System.out.println("[PIPELINE] San sang lay log tu Queue, boc tach va phat tan...");
                int logIndex = 0;

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        String rawLog = LogQueue.take();
                        logIndex++;

                        // Boc tach va chuan hoa log
                        LogEvent event = LogParser.parse(rawLog);

                        if (event != null) {
                            String effectiveIp = extractEffectiveIp(rawLog, event.getIp());

                            System.out.println("\n[SERVER NHAN LOG #" + logIndex + "]: " + rawLog);
                            System.out.println("   -> [PARSED] IP: " + effectiveIp
                                    + " | Thoi gian: " + event.getTimestamp()
                                    + " | Level: " + event.getSeverityLevel()
                                    + " | Noi dung: " + event.getMessage());

                            // Kiem tra dau hieu bat thuong (Brute-force login / Port scan)
                            checkAnomaly(detector, event, rawLog, effectiveIp);

                            // Phat tan log dang JSON qua WebSocket toi Dashboard
                            String logJson = String.format(
                                "{\"type\":\"LOG\",\"index\":%d,\"ip\":\"%s\",\"timestamp\":\"%s\",\"level\":\"%s\",\"message\":\"%s\",\"raw\":\"%s\"}",
                                logIndex,
                                escapeJson(effectiveIp),
                                escapeJson(event.getTimestamp()),
                                escapeJson(event.getSeverityLevel()),
                                escapeJson(event.getMessage()),
                                escapeJson(rawLog)
                            );
                            wsServer.broadcast(logJson);
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("[PIPELINE ERROR] " + e.getMessage());
                    }
                }
            });

            // 4. Khoi dong IngestionEngine (lang nghe UDP va TCP)
            System.out.println("[CORE] Dang mo cong UDP " + UDP_PORT + " va TCP " + TCP_PORT + "...");
            IngestionEngine engine = new IngestionEngine(UDP_PORT, TCP_PORT);
            engine.start();

            System.out.println("==================================================");
            System.out.println("   SERVER DANG CHAY! SAN SANG PHUC VU");
            System.out.println("   - UDP Ingestion   : Cong " + UDP_PORT);
            System.out.println("   - TCP Ingestion   : Cong " + TCP_PORT);
            System.out.println("   - WebSocket Stream: Cong " + WS_PORT + " (ws://localhost:" + WS_PORT + ")");
            System.out.println("==================================================");

            // Shutdown hook de don dep tai nguyen khi dung chuong trinh
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Dang dung cac tien trinh Server...");
                engine.stop();
                wsServer.stop();
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