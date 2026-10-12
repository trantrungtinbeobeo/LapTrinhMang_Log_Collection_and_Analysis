
package com.Logcollector.agent.Sender;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class UDPSender {

    private final String host;
    private final int port;

    public UDPSender(String host, int port) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host không được để trống");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port không hợp lệ");
        }

        this.host = host;
        this.port = port;
    }

    public void send(String message) throws IOException {
        if (message == null) {
            throw new IllegalArgumentException("Thông điệp không được null");
        }

        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        InetAddress address = InetAddress.getByName(host);

        DatagramPacket packet = new DatagramPacket(
                data, data.length, address, port
        );

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(packet);
        }
    }
}
