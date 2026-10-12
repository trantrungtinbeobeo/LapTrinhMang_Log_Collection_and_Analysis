
package com.Logcollector.agent;

import java.util.List;
import java.util.Locale;

public class ClientAgentMain {

    private static final int DEFAULT_EVENT_COUNT = 10;

    public static void main(String[] args) {
        String format = args.length > 0
                ? args[0].toLowerCase(Locale.ROOT)
                : "syslog";

        int eventCount = parseEventCount(args);

        EventGenerator eventGenerator = new EventGenerator();
        List<EventGenerator.LogEvent> events =
                eventGenerator.generateEvents(eventCount);

        AgentLocalQueue queue = new AgentLocalQueue();

        // Dua tat ca log vao Queue
        for (EventGenerator.LogEvent event : events) {
            queue.enqueue(event);
        }

        switch (format) {
            case "json":
                JsonFormatter jsonFormatter = new JsonFormatter();
                System.out.println("=== CLIENT AGENT - JSON OUTPUT ===");

                while (!queue.isEmpty()) {
                    System.out.println(
                            jsonFormatter.format(queue.dequeue()));
                }
                break;

            case "syslog":
            case "rfc5424":
                SyslogFormatter syslogFormatter = new SyslogFormatter();
                System.out.println(
                        "=== CLIENT AGENT - SYSLOG RFC 5424 OUTPUT ===");

                while (!queue.isEmpty()) {
                    System.out.println(
                            syslogFormatter.format(queue.dequeue()));
                }
                break;

            case "rfc3164":
                SyslogFormatter legacyFormatter = new SyslogFormatter();
                System.out.println(
                        "=== CLIENT AGENT - SYSLOG RFC 3164 OUTPUT ===");

                while (!queue.isEmpty()) {
                    System.out.println(
                            legacyFormatter.format3164(queue.dequeue()));
                }
                break;

            case "queue":
                System.out.println("=== QUEUE TEST ===");
                System.out.println("So log da them: " + queue.size());

                while (!queue.isEmpty()) {
                    EventGenerator.LogEvent event = queue.dequeue();
                    System.out.println(
                            event.getSeverity() + " | "
                            + event.getEventType() + " | "
                            + event.getMessage());
                }

                System.out.println("Queue con log: " + queue.size());
                break;

            default:
                System.err.println(
                        "Dinh dang khong hop le. Dung: json, syslog, rfc5424, rfc3164 hoac queue");
                return;
        }

        System.out.println("=== HOAN TAT ===");
        System.out.println("Tong so su kien: " + events.size());
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
}
