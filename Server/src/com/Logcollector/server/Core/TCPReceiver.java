package com.Logcollector.server.Core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPReceiver implements Runnable {
    private final int port;
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();

    public TCPReceiver(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[TCP Receiver] Dang lang nghe tren cong TCP " + port + "...");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                clientPool.submit(() -> handleClient(clientSocket));
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("[TCP Receiver ERROR] " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    if (!LogQueue.offer(line)) {
                        System.err.println("[TCP Receiver WARNING] LogQueue day! Drop goi.");
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ignored) {}
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
        clientPool.shutdownNow();
    }
}