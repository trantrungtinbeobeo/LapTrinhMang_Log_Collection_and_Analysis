
package com.Logcollector.agent.Sender;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TCPSender {

    private final String host;
    private final int port;
    private final int timeoutMillis;

    public TCPSender(String host, int port) {
        this(host, port, 5000);
    }

    public TCPSender(String host, int port, int timeoutMillis) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host khong duoc de trong");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port khong hop le");
        }

        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout phai lon hon 0");
        }

        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    public void send(String message) throws IOException {
        if (message == null) {
            throw new IllegalArgumentException("Thong diep khong duoc null");
        }

        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(host, port),
                    timeoutMillis
            );

            socket.setSoTimeout(timeoutMillis);

            OutputStream output = socket.getOutputStream();
            output.write((message + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }
}
