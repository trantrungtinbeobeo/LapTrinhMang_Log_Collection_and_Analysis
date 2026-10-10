package com.Logcollector.server.Core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class UDPReceiver implements Runnable {
    private final int port;
    private volatile boolean running = true;
    private DatagramSocket socket;

    public UDPReceiver(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            System.out.println("[UDP Receiver] Dang lang nghe tren cong UDP " + port + "...");
            byte[] buffer = new byte[65507]; // Kích thước tối đa của UDP datagram

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String rawLog = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                
                // Đẩy vào hàng đợi đệm an toàn
                if (!LogQueue.offer(rawLog)) {
                    System.err.println("[UDP Receiver WARNING] LogQueue day! Bi drop goi tin tu " + packet.getAddress());
                }
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("[UDP Receiver ERROR] " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}