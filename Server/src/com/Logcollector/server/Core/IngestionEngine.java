package com.Logcollector.server.Core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class IngestionEngine {
    private final int udpPort;
    private final int tcpPort;
    private UDPReceiver udpReceiver;
    private TCPReceiver tcpReceiver;
    private Thread udpThread;
    private Thread tcpThread;

    public IngestionEngine(int udpPort, int tcpPort) {
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;
    }

    public void start() {
        udpReceiver = new UDPReceiver(udpPort);
        tcpReceiver = new TCPReceiver(tcpPort);

        udpThread = new Thread(udpReceiver, "UDP-Receiver-Thread");
        tcpThread = new Thread(tcpReceiver, "TCP-Receiver-Thread");

        udpThread.start();
        tcpThread.start();
        System.out.println("[Ingestion Engine] Khoi dong thanh cong Engine thu thap da luong.");
    }

    public void stop() {
        if (udpReceiver != null) udpReceiver.stop();
        if (tcpReceiver != null) tcpReceiver.stop();
    }

    // Hàm kiểm thử độc lập nhanh ngay tại class này
    public static void main(String[] args) {
        int testUdpPort = 5140; // Dùng port test để tránh trùng quyền admin
        int testTcpPort = 5141;

        IngestionEngine engine = new IngestionEngine(testUdpPort, testTcpPort);
        engine.start();

        // Luồng giả lập rút log từ hàng đợi và in ra console
        Thread consumerThread = new Thread(() -> {
            try {
                while (true) {
                    String log = LogQueue.take();
                    System.out.println("[QUEUE CONSUMED TEST] Nhan log tu Queue: " + log);
                }
            } catch (InterruptedException ignored) {}
        });
        consumerThread.setDaemon(true);
        consumerThread.start();

        // Giả lập bắn thử 1 gói tin UDP kiểm thử
        try {
            Thread.sleep(500);
            System.out.println("--- DANG BAN GOI TIN TEST QUA UDP " + testUdpPort + " ---");
            DatagramSocket clientSocket = new DatagramSocket();
            String testMsg = "<14>1 2026-10-10T00:00:00Z 192.168.1.100 WebApp - - [auth@token123] Test UDP Log Packet";
            byte[] data = testMsg.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), testUdpPort);
            clientSocket.send(packet);
            clientSocket.close();

            Thread.sleep(1000);
            System.out.println("=== KIEM THU INGESTION ENGINE HOAN TAT THANH CONG ===");
            engine.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}