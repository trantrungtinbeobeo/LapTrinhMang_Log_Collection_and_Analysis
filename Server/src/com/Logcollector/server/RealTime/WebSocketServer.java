package com.Logcollector.server.RealTime;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;

/**
 * WebSocket Server (Issue #10):
 * Lắng nghe kết nối từ Dashboard Web/Client qua giao thức WebSocket (RFC 6455).
 * Cung cấp chức năng Broadcast để đẩy log và cảnh báo an ninh thời gian thực.
 */
public class WebSocketServer {

    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;

    // Danh sách các Client (Dashboard) đang kết nối: Quản lý Thread-safe
    private final Set<ClientHandler> connectedClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public WebSocketServer(int port) {
        this.port = port;
    }

    /**
     * Khởi động WebSocket Server trên một luồng riêng biệt
     */
    public void start() {
        isRunning = true;
        threadPool.submit(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("[WEBSOCKET] Server dang lang nghe tai cong: " + port);

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket);
                    connectedClients.add(handler);
                    threadPool.submit(handler);
                }
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("[WEBSOCKET] Loi ServerSocket: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Dừng WebSocket Server
     */
    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler client : connectedClients) {
                client.close();
            }
            threadPool.shutdown();
            System.out.println("[WEBSOCKET] Server da dung hoat dong.");
        } catch (IOException e) {
            System.err.println("[WEBSOCKET] Loi khi dong server: " + e.getMessage());
        }
    }

    /**
     * Đẩy tin nhắn (Log mới / Cảnh báo bất thường) tới TẤT CẢ các Client đang kết nối
     * @param jsonOrTextMessage Chuỗi dữ liệu cần gửi xuống Dashboard
     */
    public void broadcast(String jsonOrTextMessage) {
        System.out.println("[WEBSOCKET BROADCAST] -> " + jsonOrTextMessage);
        for (ClientHandler client : connectedClients) {
            client.sendMessage(jsonOrTextMessage);
        }
    }

    /**
     * Lớp xử lý từng kết nối Client (bắt tay Handshake và gửi dữ liệu dạng Frame WebSocket)
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private InputStream in;
        private OutputStream out;
        private boolean isHandshakeComplete = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();

                // 1. Thực hiện bắt tay WebSocket (Handshake)
                if (doHandshake()) {
                    isHandshakeComplete = true;
                    System.out.println("[WEBSOCKET] Client ket noi thanh cong: " + socket.getRemoteSocketAddress());

                    // Đọc dữ liệu gửi từ client (nếu có ping/pong hoặc đóng kết nối)
                    byte[] buffer = new byte[1024];
                    while (isHandshakeComplete && in.read(buffer) != -1) {
                        // Client vẫn đang giữ kết nối mở
                    }
                }
            } catch (Exception e) {
                // Client ngắt kết nối
            } finally {
                close();
            }
        }

        // Xử lý WebSocket Handshake chuẩn RFC 6455
        private boolean doHandshake() throws Exception {
            Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name());
            String data = scanner.useDelimiter("\\r\\n\\r\\n").next();
            Matcher matcher = java.util.regex.Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);

            if (matcher.find()) {
                String key = matcher.group(1).trim();
                // Thuật toán mã hóa key theo chuẩn WebSocket: SHA-1 + Base64 với chuỗi GUID cố định
                String acceptKey = Base64.getEncoder().encodeToString(
                        MessageDigest.getInstance("SHA-1").digest(
                                (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)
                        )
                );

                byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);

                out.write(response);
                out.flush();
                return true;
            }
            return false;
        }

        // Đóng gói tin nhắn dạng WebSocket Text Frame (Opcode 0x1) và gửi đi
        public synchronized void sendMessage(String message) {
            try {
                if (!socket.isClosed() && isHandshakeComplete) {
                    byte[] rawData = message.getBytes(StandardCharsets.UTF_8);
                    ByteArrayOutputStream frame = new ByteArrayOutputStream();

                    frame.write(0x81); // FIN bit = 1, Opcode = 0x1 (Text frame)

                    if (rawData.length <= 125) {
                        frame.write(rawData.length);
                    } else if (rawData.length <= 65535) {
                        frame.write(126);
                        frame.write((rawData.length >> 8) & 0xFF);
                        frame.write(rawData.length & 0xFF);
                    } else {
                        frame.write(127);
                        for (int i = 7; i >= 0; i--) {
                            frame.write((int) ((rawData.length >> (8 * i)) & 0xFF));
                        }
                    }

                    frame.write(rawData);
                    out.write(frame.toByteArray());
                    out.flush();
                }
            } catch (IOException e) {
                close();
            }
        }

        public void close() {
            try {
                isHandshakeComplete = false;
                connectedClients.remove(this);
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    // ==========================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== BAT DAU KIEM THU WEBSOCKET SERVER (ISSUE #10) ===");
        int testPort = 8085;
        WebSocketServer server = new WebSocketServer(testPort);
        server.start();

        Thread.sleep(1000); // Đợi server khởi động

        // Giả lập đẩy dữ liệu log và cảnh báo thời gian thực xuống Dashboard
        System.out.println("\n--- [TEST] Gia lap broadcast du lieu xuong Dashboard ---");
        server.broadcast("{\"type\": \"LOG\", \"message\": \"Client Agent 192.168.1.10 vua ket noi.\"}");
        server.broadcast("{\"type\": \"ALERT\", \"level\": \"HIGH\", \"message\": \"Phat hien Brute-force tu IP 192.168.1.100!\"}");
        server.broadcast("{\"type\": \"METRIC\", \"activeConnections\": 5, \"alertCount\": 1}");

        Thread.sleep(1000);
        System.out.println("\n=== KIEM THU WEBSOCKET HOAN TAT THANH CONG ===");
        server.stop();
    }
}