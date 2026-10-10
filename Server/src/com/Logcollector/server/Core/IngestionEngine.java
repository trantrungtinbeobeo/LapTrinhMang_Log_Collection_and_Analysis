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