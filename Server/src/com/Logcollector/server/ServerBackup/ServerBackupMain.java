package com.Logcollector.server.ServerBackup;

import com.Logcollector.server.Core.IngestionEngine;
import com.Logcollector.server.Core.LogQueue;
import com.Logcollector.server.Parser.LogEvent;
import com.Logcollector.server.Parser.LogParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server 2 (ServerBackup) - May chu du phong va mo rong nang luc cum (Cluster Expansion Node).
 * Chuc nang:
 * 1. Khoi dong CSDL logs_backup.db rieng biet.
 * 2. Ket noi toi Server 1 qua TCP 9997, gui CLUSTER_JOIN de dong bo du lieu ban dau.
 * 3. Nghe Replication Stream tu Server 1 theo thoi gian thuc.
 * 4. Mo cong UDP 515 va TCP 9998 rieng de chia tai tiep nhan log khi he thong vuot nguong 2.000 log.
 */
public class ServerBackupMain {

    private static String primaryHost = System.getProperty("primary.host", "192.168.100.71");
    private static int primarySyncPort = ClusterProtocol.DEFAULT_SYNC_PORT;
    private static int backupUdpPort = 515;
    private static int backupTcpPort = 9998;
    private static volatile boolean running = true;

    private static final Pattern LOG_OBJ_PATTERN = Pattern.compile(
            "\\{\"ip\":\"([^\"]*)\",\"timestamp\":\"([^\"]*)\",\"facility\":(\\d+),\"level\":\"([^\"]*)\",\"message\":\"([^\"]*)\"\\}"
    );

    public static void main(String[] args) {
        parseArguments(args);

        System.out.println("===============================================================");
        System.out.println("       LOG COLLECTOR - SERVER THU 2 (SERVER BACKUP / NODE 2)  ");
        System.out.println("===============================================================");
        System.out.println("   • May chu chinh (Primary Server 1) : " + primaryHost + ":" + primarySyncPort);
        System.out.println("   • Cong tiep nhan chia tai rieng     : UDP " + backupUdpPort + " / TCP " + backupTcpPort);
        System.out.println("   • Co so du lieu SQLite luu tru     : logs_backup.db");
        System.out.println("===============================================================\n");

        // 1. Khoi tao CSDL logs_backup.db
        BackupDatabaseManager.initializeDatabase();

        // 2. Khoi dong Ingestion Engine rieng cua Server 2 (nhan log truc tiep hoac chia tai)
        IngestionEngine engine = new IngestionEngine(backupUdpPort, backupTcpPort);
        engine.start();

        // Luong ghi log tiep nhan truc tiep tren Server 2 vao logs_backup.db
        ExecutorService consumer = Executors.newSingleThreadExecutor();
        consumer.submit(() -> {
            List<LogEvent> batch = new ArrayList<>();
            while (running) {
                try {
                    String raw = LogQueue.poll(300, TimeUnit.MILLISECONDS);
                    if (raw != null) {
                        LogEvent ev = LogParser.parse(raw);
                        if (ev != null) {
                            batch.add(ev);
                            if (batch.size() >= 50) {
                                BackupDatabaseManager.insertLogBatch(new ArrayList<>(batch));
                                batch.clear();
                            }
                        }
                    } else if (!batch.isEmpty()) {
                        BackupDatabaseManager.insertLogBatch(new ArrayList<>(batch));
                        batch.clear();
                    }
                } catch (Exception ignored) {}
            }
        });

        // 3. Luong ket noi va dong bo voi Server 1
        ExecutorService syncThread = Executors.newSingleThreadExecutor();
        syncThread.submit(ServerBackupMain::maintainSyncWithPrimary);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            System.out.println("\n[SHUTDOWN] Dang dung Server 2...");
            engine.stop();
            consumer.shutdownNow();
            syncThread.shutdownNow();
        }));
    }

    private static void maintainSyncWithPrimary() {
        while (running) {
            try {
                System.out.println("[CLUSTER CLIENT] Dang thu ket noi toi Server 1 (" + primaryHost + ":" + primarySyncPort + ")...");
                try (Socket socket = new Socket(primaryHost, primarySyncPort);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                    System.out.println("\n=======================================================");
                    System.out.println("  [KET NOI THANH CONG] DA BAT TAY DONG BO VOI SERVER 1!");
                    System.out.println("  ==> GIOI HAN 2.000 LOG TREN SERVER 1 DA DUOC GO BO!");
                    System.out.println("=======================================================\n");

                    // Gui ban tin CLUSTER_JOIN
                    String joinMsg = String.format("{\"action\":\"%s\",\"nodeName\":\"Server2-Backup\",\"backupTcp\":%d,\"backupUdp\":%d}",
                            ClusterProtocol.ACTION_CLUSTER_JOIN, backupTcpPort, backupUdpPort);
                    writer.println(joinMsg);

                    // Luong dinh ky gui Heartbeat moi 5 giay
                    long lastHeartbeat = System.currentTimeMillis();

                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        handleIncomingClusterMessage(line, writer);

                        if (System.currentTimeMillis() - lastHeartbeat >= 5000) {
                            writer.println("{\"action\":\"" + ClusterProtocol.ACTION_HEARTBEAT + "\"}");
                            lastHeartbeat = System.currentTimeMillis();
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[CLUSTER CLIENT] Chua ket noi duoc voi Server 1 (" + e.getMessage() + "). Thu lai sau 3 giay...");
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                break;
            }
        }
    }

    private static int totalSyncedLogs = 0;

    private static void handleIncomingClusterMessage(String json, PrintWriter writer) {
        if (json == null || json.isBlank()) return;

        if (json.contains(ClusterProtocol.ACTION_CLUSTER_ACK)) {
            System.out.println("[CLUSTER SYNC] Server 1 da chap nhan ket noi. San sang nhan luong du lieu...");
            return;
        }

        if (json.contains(ClusterProtocol.ACTION_SYNC_BATCH)) {
            // Parse danh sach log trong batch
            List<LogEvent> batch = parseLogBatch(json);
            if (!batch.isEmpty()) {
                BackupDatabaseManager.insertLogBatch(batch);
                totalSyncedLogs += batch.size();
                printBackupProgress(totalSyncedLogs, "ĐANG ĐỒNG BỘ CSDL TỪ SERVER 1");
                if (writer != null) {
                    writer.println(String.format("{\"action\":\"%s\",\"synced\":%d}",
                            ClusterProtocol.ACTION_SYNC_PROGRESS, totalSyncedLogs));
                }
            }
            return;
        }

        if (json.contains(ClusterProtocol.ACTION_REPLICATE_LOG)) {
            // Nhan log thoi gian thuc stream tu Server 1
            LogEvent ev = parseSingleLogEvent(json);
            if (ev != null) {
                BackupDatabaseManager.insertSingleLog(ev);
                totalSyncedLogs++;
                printBackupProgress(totalSyncedLogs, "NHÂN BẢN THỜI GIAN THỰC (REPLICATION)");
                if (writer != null && totalSyncedLogs % 10 == 0) {
                    writer.println(String.format("{\"action\":\"%s\",\"synced\":%d}",
                            ClusterProtocol.ACTION_SYNC_PROGRESS, totalSyncedLogs));
                }
            }
        }
    }

    private static void printBackupProgress(int count, String stage) {
        String time = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        int barLength = 20;
        int fill = Math.min(barLength, (count * barLength) / 2000);
        String bar = "█".repeat(Math.max(0, fill)) + "░".repeat(Math.max(0, barLength - fill));
        System.out.printf("[%s] [SERVER 2 BACKUP] [%s] %,d logs trong logs_backup.db | %s%n",
                time, bar, count, stage);
    }

    private static List<LogEvent> parseLogBatch(String json) {
        List<LogEvent> list = new ArrayList<>();
        Matcher m = LOG_OBJ_PATTERN.matcher(json);
        while (m.find()) {
            list.add(new LogEvent(
                    m.group(1),
                    m.group(2),
                    Integer.parseInt(m.group(3)),
                    m.group(4),
                    m.group(5)
            ));
        }
        return list;
    }

    private static LogEvent parseSingleLogEvent(String json) {
        Matcher m = LOG_OBJ_PATTERN.matcher(json);
        if (m.find()) {
            return new LogEvent(
                    m.group(1),
                    m.group(2),
                    Integer.parseInt(m.group(3)),
                    m.group(4),
                    m.group(5)
            );
        }
        return null;
    }

    private static void parseArguments(String[] args) {
        if (args == null) return;
        for (int i = 0; i < args.length; i++) {
            if ("--primary".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                primaryHost = args[++i].trim();
            } else if ("--sync-port".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try { primarySyncPort = Integer.parseInt(args[++i].trim()); } catch (NumberFormatException ignored) {}
            } else if ("--udp".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try { backupUdpPort = Integer.parseInt(args[++i].trim()); } catch (NumberFormatException ignored) {}
            } else if ("--tcp".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try { backupTcpPort = Integer.parseInt(args[++i].trim()); } catch (NumberFormatException ignored) {}
            }
        }
        String sysPrimary = System.getProperty("primary.host");
        if (sysPrimary != null && !sysPrimary.isBlank()) {
            primaryHost = sysPrimary.trim();
        }
    }
}
