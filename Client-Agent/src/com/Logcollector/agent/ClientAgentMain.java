
package com.Logcollector.agent;

import java.util.List;

public class ClientAgentMain {

    public static void main(String[] args) {
        EventGenerator generator = new EventGenerator();
        SyslogFormatter formatter = new SyslogFormatter();

        List<String> events = generator.generateEvents(10);

        System.out.println("=== CLIENT AGENT - SYSLOG OUTPUT ===");

        for (String event : events) {
            String[] parts = event.split(" \\| ", 4);

            if (parts.length != 4) {
                System.err.println("Bo qua su kien khong hop le: " + event);
                continue;
            }

            String severity = parts[1];
            String eventType = parts[2];
            String message = parts[3];

            String syslog = formatter.format(
                    severity,
                    eventType,
                    message
            );

            System.out.println(syslog);
        }

        System.out.println("=== HOAN TAT ===");
    }
}
