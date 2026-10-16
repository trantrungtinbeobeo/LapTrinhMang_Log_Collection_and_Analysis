package com.Logcollector.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tao cac su kien mang/he thong gia lap cho Client Agent.
 */
public class EventGenerator {

    public static class LogEvent {
        private final String timestamp;
        private final String severity;
        private final String eventType;
        private final String message;
        private final String device;
        private final String category;

        public LogEvent(String timestamp, String severity,
                        String eventType, String message) {
            this(timestamp, severity, eventType, message, SyslogFormatter.detectHostname(), determineCategory(eventType));
        }

        public LogEvent(String timestamp, String severity,
                        String eventType, String message, String device, String category) {
            this.timestamp = timestamp;
            this.severity = severity;
            this.eventType = eventType;
            this.message = message;
            this.device = (device != null && !device.trim().isEmpty()) ? device : SyslogFormatter.detectHostname();
            this.category = (category != null && !category.trim().isEmpty()) ? category : determineCategory(eventType);
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getSeverity() {
            return severity;
        }

        public String getEventType() {
            return eventType;
        }

        public String getMessage() {
            return message;
        }

        public String getDevice() {
            return device;
        }

        public String getCategory() {
            return category;
        }
    }

    public static String determineCategory(String eventType) {
        if (eventType == null) return "APPLICATION";
        String upper = eventType.toUpperCase();
        if (upper.contains("LOGIN") || upper.contains("AUTH")) return "AUTH";
        if (upper.contains("PORT_SCAN") || upper.contains("ATTACK") || upper.contains("BRUTE_FORCE")) return "SECURITY";
        if (upper.contains("TRAFFIC") || upper.contains("SPIKE") || upper.contains("TELEMETRY") || upper.contains("LATENCY") || upper.contains("POLL")) return "NETWORK";
        if (upper.contains("DEVICE") || upper.contains("CPU") || upper.contains("SYSTEM") || upper.contains("CONFIG")) return "SYSTEM";
        return "APPLICATION";
    }

    private static final String[] SEVERITIES = {
        "INFO", "WARNING", "ERROR", "CRITICAL"
    };

    private static final String[] EVENT_TYPES = {
        "LOGIN_FAILED",
        "LOGIN_SUCCESS",
        "CONNECTION_TIMEOUT",
        "HTTP_404",
        "PORT_SCAN_SIMULATED"
    };

    private static final String[] MESSAGES = {
        "Failed login attempt",
        "User login successful",
        "Network connection timed out",
        "Requested resource not found",
        "Simulated port scan detected"
    };

    /**
     * Tao mot su kien ngau nhien.
     */
    public LogEvent generateEvent() {
        int index = ThreadLocalRandom.current()
                .nextInt(EVENT_TYPES.length);

        String eventType = EVENT_TYPES[index];
        String severity = getSeverity(eventType);

        return new LogEvent(
                Instant.now().toString(),
                severity,
                eventType,
                MESSAGES[index]
        );
    }

    /**
     * Tao danh sach su kien.
     */
    public List<LogEvent> generateEvents(int count) {
        if (count < 0) {
            throw new IllegalArgumentException(
                    "Event count must not be negative"
            );
        }

        List<LogEvent> events = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            events.add(generateEvent());
        }

        return events;
    }

    private String getSeverity(String eventType) {
        switch (eventType) {
            case "LOGIN_SUCCESS":
                return "INFO";

            case "LOGIN_FAILED":
                return "WARNING";

            case "CONNECTION_TIMEOUT":
            case "HTTP_404":
                return "ERROR";

            case "PORT_SCAN_SIMULATED":
                return "CRITICAL";

            default:
                return SEVERITIES[0];
        }
    }

    /**
     * Chay thu rieng EventGenerator.
     */
    public static void main(String[] args) {
        EventGenerator generator = new EventGenerator();

        for (LogEvent event : generator.generateEvents(10)) {
            System.out.printf(
                    "%s | %s | %s | %s%n",
                    event.getTimestamp(),
                    event.getSeverity(),
                    event.getEventType(),
                    event.getMessage()
            );
        }
    }
}
