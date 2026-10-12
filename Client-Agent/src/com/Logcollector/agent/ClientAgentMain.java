
package com.Logcollector.agent;

import com.Logcollector.agent.Sender.TCPSender;
import com.Logcollector.agent.Sender.UDPSender;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ClientAgentMain {

    private static final int DEFAULT_EVENT_COUNT = 10;
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_TCP_PORT = 9999;
    private static final int DEFAULT_UDP_PORT = 514;
    private static final long RETRY_DELAY_MS = 3000;

    public static void main(String[] args) {
        String mode = args.length > 0
                ? args[0].toLowerCase(Locale.ROOT)
                : "syslog";

        int eventCount = parseEventCount(args);

        EventGenerator generator = new EventGenerator();
        List<EventGenerator.LogEvent> events =
                generator.generateEvents(eventCount);

        AgentLocalQueue queue = new AgentLocalQueue();

        for (EventGenerator.LogEvent event : events) {
            queue.enqueue(event);
        }

        switch (mode) {
            case "json": {
                JsonFormatter formatter = new JsonFormatter();
                while (!queue.isEmpty()) {
                    System.out.println(formatter.format(queue.dequeue()));
                }
                break;
            }

            case "syslog":
            case "rfc5424": {
                SyslogFormatter formatter = new SyslogFormatter();
                while (!queue.isEmpty()) {
                    System.out.println(formatter.format(queue.dequeue()));
                }
                break;
            }

            case "rfc3164": {
                SyslogFormatter formatter = new SyslogFormatter();
                while (!queue.isEmpty()) {
                    System.out.println(
                            formatter.format3164(queue.dequeue()));
                }
                break;
            }

            case "queue":
                testQueue(queue);
                break;

            case "tcp":
                sendTcp(queue, args);
                break;

            case "udp":
                sendUdp(queue, args);
                break;

            case "auto":
                sendAutomatically(queue, args);
                break;

            default:
                System.err.println(
                        "Che do hop le: json, syslog, rfc5424, "
                        + "rfc3164, queue, tcp, udp, auto");
                return;
        }

        System.out.println("=== HOAN TAT ===");
        System.out.println("Tong so su kien: " + events.size());
        System.out.println("So log con trong Queue: " + queue.size());
    }

    private static void testQueue(AgentLocalQueue queue) {
        System.out.println("=== QUEUE TEST ===");
        System.out.println("So log da them: " + queue.size());

        while (!queue.isEmpty()) {
            EventGenerator.LogEvent event = queue.dequeue();
            System.out.println(
                    event.getSeverity() + " | "
                    + event.getEventType() + " | "
                    + event.getMessage());
        }
    }

    private static void sendTcp(
            AgentLocalQueue queue, String[] args) {
        String host = args.length > 2 ? args[2] : DEFAULT_HOST;
        int port = parsePort(args, 3, DEFAULT_TCP_PORT);

        SyslogFormatter formatter = new SyslogFormatter();
        TCPSender sender = new TCPSender(host, port);

        sendQueue(queue, event -> sender.send(formatter.format(event)));
    }

    private static void sendUdp(
            AgentLocalQueue queue, String[] args) {
        String host = args.length > 2 ? args[2] : DEFAULT_HOST;
        int port = parsePort(args, 3, DEFAULT_UDP_PORT);

        SyslogFormatter formatter = new SyslogFormatter();
        UDPSender sender = new UDPSender(host, port);

        sendQueue(queue, event -> sender.send(formatter.format(event)));
    }

    private static void sendAutomatically(
            AgentLocalQueue queue, String[] args) {

        String host = args.length > 2 ? args[2] : DEFAULT_HOST;
        int tcpPort = parsePort(args, 3, DEFAULT_TCP_PORT);
        int udpPort = parsePort(args, 4, DEFAULT_UDP_PORT);

        SyslogFormatter formatter = new SyslogFormatter();
        TCPSender tcpSender = new TCPSender(host, tcpPort);
        UDPSender udpSender = new UDPSender(host, udpPort);

        System.out.println("=== AUTO MODE: UDP / TCP ===");
        System.out.println("INFO, WARNING -> UDP");
        System.out.println("ERROR, CRITICAL -> TCP");

        while (!queue.isEmpty()) {
            EventGenerator.LogEvent event = queue.peek();

            if (event == null) {
                break;
            }

            boolean useTcp =
                    "ERROR".equalsIgnoreCase(event.getSeverity())
                    || "CRITICAL".equalsIgnoreCase(event.getSeverity());

            try {
                String message = formatter.format(event);

                if (useTcp) {
                    tcpSender.send(message);
                } else {
                    udpSender.send(message);
                }

                queue.dequeue();

                System.out.println(
                        "Da gui qua " + (useTcp ? "TCP" : "UDP")
                        + ": " + event.getEventType()
                        + " | " + event.getSeverity());

            } catch (IOException e) {
                System.err.println(
                        "Gui that bai cho log "
                        + event.getEventType() + ": "
                        + e.getMessage());

                System.err.println(
                        "Giu log trong Queue, thu lai sau "
                        + (RETRY_DELAY_MS / 1000) + " giay.");

                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    System.err.println(
                            "Da dung retry. Log chua gui van nam trong Queue.");
                    return;
                }
            }
        }
    }

    private static void sendQueue(
            AgentLocalQueue queue, LogSender action) {

        System.out.println("So log trong Queue: " + queue.size());

        while (!queue.isEmpty()) {
            EventGenerator.LogEvent event = queue.peek();

            try {
                action.send(event);
                queue.dequeue();

                System.out.println(
                        "Da gui log: "
                        + event.getEventType()
                        + " | " + event.getSeverity());

            } catch (IOException e) {
                System.err.println("Gui log that bai: " + e.getMessage());
                System.err.println("Log van duoc giu trong Queue.");
                break;
            }
        }
    }

    private static int parsePort(
            String[] args, int index, int defaultPort) {

        if (args.length <= index) {
            return defaultPort;
        }

        try {
            int port = Integer.parseInt(args[index]);

            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }

            return port;
        } catch (NumberFormatException e) {
            System.err.println(
                    "Port khong hop le, su dung port mac dinh "
                    + defaultPort);
            return defaultPort;
        }
    }

    private static int parseEventCount(String[] args) {
        if (args == null || args.length < 2) {
            return DEFAULT_EVENT_COUNT;
        }

        try {
            int count = Integer.parseInt(args[1]);

            if (count < 0) {
                System.err.println(
                        "So luong su kien khong duoc am. Dung mac dinh.");
                return DEFAULT_EVENT_COUNT;
            }

            return count;
        } catch (NumberFormatException e) {
            System.err.println(
                    "So luong su kien khong hop le. Dung mac dinh.");
            return DEFAULT_EVENT_COUNT;
        }
    }

    @FunctionalInterface
    private interface LogSender {
        void send(EventGenerator.LogEvent event) throws IOException;
    }
}
