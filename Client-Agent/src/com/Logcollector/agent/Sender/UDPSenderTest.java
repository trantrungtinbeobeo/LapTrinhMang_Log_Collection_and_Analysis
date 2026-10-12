
package com.Logcollector.agent.Sender;

public class UDPSenderTest {

    public static void main(String[] args) {
        try {
            UDPSender sender = new UDPSender("127.0.0.1", 514);
            sender.send("Test log tu Client Agent");

            System.out.println("Da gui goi tin UDP.");
        } catch (Exception e) {
            System.err.println("Gui UDP that bai: " + e.getMessage());
        }
    }
}
