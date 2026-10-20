package com.Logcollector.server.ServerBackup;

import com.Logcollector.server.Parser.LogEvent;
import com.Logcollector.server.RealTime.WebSocketServer;
import com.Logcollector.server.Storage.DatabaseManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dieu phoi vien dong bo Cum tai Server 1 (Primary Server).
 * Mo Socket Server tai cong SYNC_PORT (9997) de lang nghe Server 2 ket noi.
 * Khi Server 2 ket noi, tu dong dong bo toan bo log va go bo gioi han 2.000 log!
 */
public class ClusterSyncCoordinator {

    private static volatile boolean secondaryConnected = false;
    private static volatile PrintWriter secondaryWriter = null;
    private static ServerSocket serverSocket;
    private static ExecutorService threadPool;
    private static WebSocketServer wsServerRef;
    private static Runnable onClusterExpandedCallback;

    public static synchronized void start(int syncPort, WebSocketServer wsServer, Runnable onClusterExpanded) {
        if (threadPool != null && !threadPool.isShutdown()) return;

        wsServerRef = wsServer;
        onClusterExpandedCallback = onClusterExpanded;
        threadPool = Executors.newCachedThreadPool();

        threadPool.submit(() -> {
            try {
                serverSocket = new ServerSocket(syncPort);
                serverSocket.setReuseAddress(true);
                System.out.println("[CLUSTER COORDINATOR] San sang lang nghe Server 2 tai cong TCP " + syncPort + "...");

                while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    threadPool.submit(() -> handleSecondaryConnection(socket));
                }
            } catch (Exception e) {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    System.err.println("[CLUSTER COORDINATOR ERROR] " + e.getMessage());
                }
            }
        });
    }

    private static void handleSecondaryConnection(Socket socket) {
        String clientAddress = socket.getRemoteSocketAddress().toString();
        System.out.println("\n=======================================================");
        System.out.println("  [CLUSTER JOIN] PHAT HIEN SERVER 2 KET NOI TU: " + clientAddress);
        System.out.println("  ==> HE THONG GIAI PHONG NANG LUC VƯỢT NGƯỠNG 2.000 LOG!");
        System.out.println("=======================================================\n");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            secondaryWriter = writer;
            secondaryConnected = true;

            // Kich hoat Callback go bo trang thai qua tai tren Server 1
            if (onClusterExpandedCallback != null) {
                onClusterExpandedCallback.run();
            }

            // Phat tin hieu WebSocket toi Desktop App bao Cum 2 Server da hoat dong
            if (wsServerRef != null) {
                String clusterAlert = "{\"type\":\"SYSTEM_ALERT\",\"status\":\"CLUSTER_ACTIVE\",\"message\":\"🟢 CỤM 2 SERVER HOẠT ĐỘNG: ĐÃ ĐỒNG BỘ DỮ LIỆU VÀ MỞ RỘNG VƯỢT NGƯỠNG 2.000 LOG!\"}";
                wsServerRef.broadcast(clusterAlert);
            }

            // Doc ban tin bat tay tu Server 2
            String line = reader.readLine();
            if (line != null && line.contains(ClusterProtocol.ACTION_CLUSTER_JOIN)) {
                int totalLogs = DatabaseManager.getTotalLogCount();
                String ackJson = String.format("{\"action\":\"%s\",\"primaryCapacity\":2000,\"currentLogs\":%d,\"message\":\"Welcome Server 2 to Cluster\"}",
                        ClusterProtocol.ACTION_CLUSTER_ACK, totalLogs);
                writer.println(ackJson);

                // Dong bo toan bo log hien tai tu Database sang Server 2
                syncInitialData(writer);
            }

            // Vong lap giu ket noi va nhan phan hoi tu Server 2
            while ((line = reader.readLine()) != null) {
                if (line.contains(ClusterProtocol.ACTION_HEARTBEAT)) {
                    writer.println("{\"action\":\"HEARTBEAT_ACK\"}");
                }
            }

        } catch (Exception e) {
            System.err.println("[CLUSTER COORDINATOR] Server 2 da ngat ket noi: " + e.getMessage());
        } finally {
            secondaryConnected = false;
            secondaryWriter = null;
            System.out.println("[CLUSTER COORDINATOR] Server 2 offline. He thong tro ve che do Standalone.");
            if (wsServerRef != null) {
                String offlineAlert = "{\"type\":\"SYSTEM_ALERT\",\"status\":\"STANDALONE\",\"message\":\"⚠️ Server 2 đã ngắt kết nối. Hệ thống quay về chế độ máy chủ đơn lẻ.\"}";
                wsServerRef.broadcast(offlineAlert);
            }
        }
    }

    private static void syncInitialData(PrintWriter writer) {
        List<LogEvent> currentLogs = DatabaseManager.getRecentLogs(2500);
        if (currentLogs.isEmpty()) {
            System.out.println("[CLUSTER SYNC] Server 1 chua co log de dong bo sang Server 2.");
            return;
        }

        System.out.println("[CLUSTER SYNC] Bat dau dong bo " + currentLogs.size() + " log ban dau sang Server 2...");
        int batchSize = 100;
        for (int i = 0; i < currentLogs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, currentLogs.size());
            List<LogEvent> sub = currentLogs.subList(i, end);

            StringBuilder json = new StringBuilder();
            json.append("{\"action\":\"").append(ClusterProtocol.ACTION_SYNC_BATCH).append("\",\"logs\":[");
            for (int j = 0; j < sub.size(); j++) {
                if (j > 0) json.append(",");
                LogEvent e = sub.get(j);
                json.append(String.format("{\"ip\":\"%s\",\"timestamp\":\"%s\",\"facility\":%d,\"level\":\"%s\",\"message\":\"%s\"}",
                        ClusterProtocol.escapeJson(e.getIp()),
                        ClusterProtocol.escapeJson(e.getTimestamp()),
                        e.getFacility(),
                        ClusterProtocol.escapeJson(e.getSeverityLevel()),
                        ClusterProtocol.escapeJson(e.getMessage())));
            }
            json.append("]}");
            writer.println(json.toString());
        }
        System.out.println("[CLUSTER SYNC] Hoan tat dong bo du lieu ban dau sang Server 2!");
    }

    /**
     * Nhan ban log moi sang Server 2 theo thoi gian thuc khi Server 1 tiep nhan log
     */
    public static void replicateLog(LogEvent event) {
        PrintWriter writer = secondaryWriter;
        if (secondaryConnected && writer != null && event != null) {
            try {
                String streamJson = String.format("{\"action\":\"%s\",\"ip\":\"%s\",\"timestamp\":\"%s\",\"facility\":%d,\"level\":\"%s\",\"message\":\"%s\"}",
                        ClusterProtocol.ACTION_REPLICATE_LOG,
                        ClusterProtocol.escapeJson(event.getIp()),
                        ClusterProtocol.escapeJson(event.getTimestamp()),
                        event.getFacility(),
                        ClusterProtocol.escapeJson(event.getSeverityLevel()),
                        ClusterProtocol.escapeJson(event.getMessage()));
                writer.println(streamJson);
            } catch (Exception ignored) {}
        }
    }

    public static boolean isSecondaryConnected() {
        return secondaryConnected;
    }

    public static void stop() {
        try {
            if (serverSocket != null) serverSocket.close();
            if (threadPool != null) threadPool.shutdownNow();
        } catch (Exception ignored) {}
    }
}
