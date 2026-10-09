
package com.Logcollector.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EventGenerator {

    private final Random random = new Random();

    private static final String[] EVENTS = {
        "LOGIN_SUCCESS",
        "LOGIN_FAILED",
        "CONNECTION_TIMEOUT",
        "HTTP_404",
        "PORT_SCAN_SIMULATED"
    };

    public List<String> generateEvents(int count) {
        List<String> events = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String eventType =
                    EVENTS[random.nextInt(EVENTS.length)];

            String severity = getSeverity(eventType);

            String timestamp = Instant.now().toString();

            String event = timestamp
                    + " | " + severity
                    + " | " + eventType
                    + " | Simulated network event";

            events.add(event);
        }

        return events;
    }

    private String getSeverity(String eventType) {
        switch (eventType) {
            case "LOGIN_SUCCESS":
                return "INFO";

            case "LOGIN_FAILED":
            case "HTTP_404":
                return "WARNING";

            case "CONNECTION_TIMEOUT":
                return "ERROR";

            case "PORT_SCAN_SIMULATED":
                return "CRITICAL";

            default:
                return "INFO";
        }
    }

    public static void main(String[] args) {
        EventGenerator generator = new EventGenerator();

        List<String> events = generator.generateEvents(10);

        for (String event : events) {
            System.out.println(event);
        }
    }
}
