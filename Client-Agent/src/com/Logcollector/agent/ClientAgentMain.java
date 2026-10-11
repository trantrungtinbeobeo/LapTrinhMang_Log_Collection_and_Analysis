package com.Logcollector.agent;

import java.util.List;

/**
 * Diem khoi dong cua Client Agent.
 * Ho tro xuat log theo Syslog RFC 5424 hoac JSON.
 */
public class ClientAgentMain {

    private static final int DEFAULT_EVENT_COUNT = 10;

    public static void main(String[] args) {
        String format = args.length > 0
                ? args[0].toLowerCase()
                : "syslog";

        int eventCount = parseEventCount(args);

        EventGenerator eventGenerator = new EventGenerator();
        List<EventGenerator.LogEvent> events =
                eventGenerator.generateEvents(eventCount);

        switch (format) {
            case "json":
                JsonFormatter jsonFormatter = new JsonFormatter();
                System.out.println("=== CLIENT AGENT - JSON OUTPUT ===");

                for (EventGenerator.LogEvent event : events) {
                    System.out.println(jsonFormatter.format(event));
                }
                break;

            case "syslog":
                SyslogFormatter syslogFormatter = new SyslogFormatter();
                System.out.println("=== CLIENT AGENT - SYSLOG RFC 5424 OUTPUT ===");

                for (EventGenerator.LogEvent event : events) {
                    System.out.println(syslogFormatter.format(event));
                }
                break;

            default:
                System.err.println(
                        "Dinh dang khong hop le. Hay dung: syslog hoac json"
                );
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
                        "So luong su kien khong duoc am. Dung mac dinh."
                );
                return DEFAULT_EVENT_COUNT;
            }

            return count;
        } catch (NumberFormatException e) {
            System.err.println(
                    "So luong su kien khong hop le. Dung mac dinh."
            );
            return DEFAULT_EVENT_COUNT;
        }
    }
}