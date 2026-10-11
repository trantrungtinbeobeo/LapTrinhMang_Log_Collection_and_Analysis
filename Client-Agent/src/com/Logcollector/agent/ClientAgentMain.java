
package com.Logcollector.agent;

import java.util.List;

/**
 * Diem khoi dong cua Client Agent.
 * Tao su kien gia lap va xuat log theo RFC 5424.
 */
public class ClientAgentMain {

    private static final int DEFAULT_EVENT_COUNT = 10;

    public static void main(String[] args) {
        int eventCount = parseEventCount(args);

        EventGenerator eventGenerator = new EventGenerator();
        SyslogFormatter syslogFormatter = new SyslogFormatter();

        List<EventGenerator.LogEvent> events =
                eventGenerator.generateEvents(eventCount);

        System.out.println("=== CLIENT AGENT - SYSLOG OUTPUT ===");

        for (EventGenerator.LogEvent event : events) {
            String syslogMessage = syslogFormatter.format(event);
            System.out.println(syslogMessage);
        }

        System.out.println("=== HOAN TAT ===");
        System.out.println("Tong so su kien: " + events.size());
    }

    /**
     * Cho phep truyen so luong su kien qua tham so dong lenh.
     * Vi du: java ...ClientAgentMain 20
     */
    private static int parseEventCount(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_EVENT_COUNT;
        }

        try {
            int count = Integer.parseInt(args[0]);

            if (count < 0) {
                System.err.println(
                        "So luong su kien khong duoc am. Dung mac dinh."
                );
                return DEFAULT_EVENT_COUNT;
            }

            return count;
        } catch (NumberFormatException e) {
            System.err.println(
                    "Tham so khong hop le. Dung mac dinh "
                    + DEFAULT_EVENT_COUNT + " su kien."
            );
            return DEFAULT_EVENT_COUNT;
        }
    }
}
