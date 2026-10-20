package com.Logcollector.server.UI;

import com.Logcollector.server.UI.desktop.DesktopFrame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.util.Enumeration;

/** Native Swing entry point. The legacy constructor is retained for ServerMain. */
public class DashboardUI {
    private JFrame frame;
    private DesktopFrame content;
    private boolean running;
    private long generation;
    private boolean initialDemo;

    public DashboardUI() { }
    public DashboardUI(int legacyHttpPort) { /* API compatibility; no HTTP server in Desktop mode. */ }

    public synchronized void start() {
        if (running) return;
        running = true;
        long token = ++generation;
        SwingUtilities.invokeLater(() -> {
            synchronized (DashboardUI.this) {
                if (!running || generation != token) return;
                if (GraphicsEnvironment.isHeadless()) {
                    running = false;
                    System.err.println("[Desktop UI] Can moi truong co man hinh de mo Swing.");
                    return;
                }
                content = DesktopFrame.create();
                frame = new JFrame("Log Collector • Dashboard Java Desktop");
                frame.setContentPane(content);
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        stop();
                        System.exit(0);
                    }
                });
                frame.setMinimumSize(new Dimension(1000, 680));
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                frame.setSize(Math.min(1380, screen.width - 60), Math.min(1000, screen.height - 70));
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                content.connect(initialDemo);
            }
        });
    }

    public synchronized void stop() {
        running = false;
        generation++;
        DesktopFrame oldContent = content;
        JFrame oldFrame = frame;
        content = null;
        frame = null;
        if (oldContent != null) oldContent.close();
        if (oldFrame != null) SwingUtilities.invokeLater(() -> {
            for (Window child : oldFrame.getOwnedWindows()) child.dispose();
            oldFrame.dispose();
        });
    }

    /** Legacy public API retained for callers; the desktop itself does not inspect browser user agents. */
    public static String parseDeviceFromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.trim().isEmpty()) {
            return "Network-Device";
        }
        if (userAgent.contains("iPhone")) return "Apple iPhone (iOS)";
        if (userAgent.contains("iPad")) return "Apple iPad (iPadOS)";
        if (userAgent.contains("Android")) {
            if (userAgent.contains("Samsung") || userAgent.contains("SM-")) return "Samsung Galaxy (Android)";
            if (userAgent.contains("Xiaomi") || userAgent.contains("Redmi")) return "Xiaomi (Android)";
            return "Android Smartphone";
        }
        if (userAgent.contains("Windows NT 10.0") || userAgent.contains("Windows NT 11.0")) {
            if (userAgent.contains("Edg/")) return "Windows PC (Edge)";
            if (userAgent.contains("Chrome/")) return "Windows PC (Chrome)";
            if (userAgent.contains("Firefox/")) return "Windows PC (Firefox)";
            return "Windows PC (Desktop)";
        }
        if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS X")) {
            return "Apple Mac / MacBook";
        }
        if (userAgent.contains("Linux")) return "Linux Workstation";
        return "Web-Client";
    }

    /**
     * Lay dia chi IP LAN thuc te cua may chu de cac may khac trong Wi-Fi/LAN co the ket noi
     */
    public static String getLocalLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String host = addr.getHostAddress();
                        if (!host.startsWith("127.")) {
                            return host;
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static void main(String[] args) {
        DashboardUI ui = new DashboardUI();
        ui.initialDemo = java.util.Arrays.asList(args).contains("--demo");
        ui.start();
    }
}
