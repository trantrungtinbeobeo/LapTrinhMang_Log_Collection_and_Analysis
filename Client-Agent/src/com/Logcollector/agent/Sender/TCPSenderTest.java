package com.Logcollector.agent.Sender;

public class TCPSenderTest {

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 9999;

        try {
            TCPSender sender = new TCPSender(host, port);
            sender.send("Test log tu Client Agent qua TCP");

            System.out.println("Da gui goi tin TCP.");
        } catch (Exception e) {
            System.err.println(
                "Gui TCP that bai: " + e.getMessage()
            );
        }
    }
}
