
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
        Instant lastTimestamp = Instant.MIN;

        for (int i = 0; i < count; i++) {
            String eventType =
                    EVENTS[random.nextInt(EVENTS.length)];

            String severity = getSeverity(eventType);

            Instant now = Instant.now();

            // Dam bao timestamp tang dan trong mot lan tao
            if (!now.isAfter(lastTimestamp)) {
                now = lastTimestamp.plusNanos(1);
            }

            lastTimestamp = now;

            String message = getMessage(eventType);

            String event = now
                + " | " + severity
                + " | " + eventType
                + " | " + message;

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

private String getMessage(String eventType) {
    switch (eventType) {
        case "LOGIN_SUCCESS":
            return "User login successful";

        case "LOGIN_FAILED":
            return "Failed login attempt";

        case "CONNECTION_TIMEOUT":
            return "Network connection timed out";

        case "HTTP_404":
            return "Requested resource not found";

        case "PORT_SCAN_SIMULATED":
            return "Simulated port scan detected";

        default:
            return "Unknown simulated event";
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