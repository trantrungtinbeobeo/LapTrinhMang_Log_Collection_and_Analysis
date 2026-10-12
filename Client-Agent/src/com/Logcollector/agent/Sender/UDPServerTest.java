
package com.Logcollector.agent.Sender;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class UDPServerTest {

    public static void main(String[] args) {
        int port = 514;
        byte[] buffer = new byte[4096];

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println(
                "UDP Server dang lang nghe cong " + port
            );

            while (true) {
                DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length
                );

                socket.receive(packet);

                String message = new String(
                    packet.getData(),
                    packet.getOffset(),
                    packet.getLength(),
                    StandardCharsets.UTF_8
                );

                System.out.println(
                    "Nhan duoc tu "
                    + packet.getAddress().getHostAddress()
                    + ": " + message
                );
            }

        } catch (Exception e) {
            System.err.println(
                "UDP Server loi: " + e.getMessage()
            );
        }
    }
}
