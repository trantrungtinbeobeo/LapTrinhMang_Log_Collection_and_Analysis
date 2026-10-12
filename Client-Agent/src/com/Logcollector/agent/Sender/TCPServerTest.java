
package com.Logcollector.agent.Sender;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TCPServerTest {

    public static void main(String[] args) {
        int port = 9999;

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println(
                "TCP Server dang lang nghe cong " + port
            );

            while (true) {
                try (
                    Socket client = server.accept();
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                            client.getInputStream(),
                            StandardCharsets.UTF_8
                        )
                    )
                ) {
                    String message = reader.readLine();
                    System.out.println("Nhan duoc: " + message);
                }
            }

        } catch (Exception e) {
            System.err.println(
                "TCP Server loi: " + e.getMessage()
            );
        }
    }
}
