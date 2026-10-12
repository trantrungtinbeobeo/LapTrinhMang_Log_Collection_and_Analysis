
package com.Logcollector.agent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class SyslogFormatter {

    private static final int FACILITY = 1;

    private static final DateTimeFormatter RFC3164_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd HH:mm:ss", Locale.US)
                    .withZone(ZoneId.systemDefault());

    private final String hostname;
    private final String appName;
    private final String procId;

    public SyslogFormatter() {
        this("client-agent", "LogCollector",
                String.valueOf(ProcessHandle.current().pid()));
    }

    public SyslogFormatter(String hostname, String appName, String procId) {
        this.hostname = sanitizeHeader(hostname);
        this.appName = sanitizeHeader(appName);
        this.procId = sanitizeHeader(procId);
    }

    private int toSyslogSeverity(String severity) {
        if (severity == null) {
            return 5;
        }

        switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL": return 2;
            case "ERROR": return 3;
            case "WARNING": return 4;
            case "INFO": return 6;
            default: return 5;
        }
    }

    private int calculatePri(String severity) {
        return FACILITY * 8 + toSyslogSeverity(severity);
    }

    // RFC 5424
    public String format(EventGenerator.LogEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event must not be null");
        }

        String timestamp = normalizeTimestamp(event.getTimestamp());
        String eventType = sanitizeHeader(event.getEventType());
        String message = sanitizeMessage(event.getMessage());
        int pri = calculatePri(event.getSeverity());

        return "<" + pri + ">1 "
                + timestamp + " "
                + hostname + " "
                + appName + " "
                + procId + " "
                + eventType + " "
                + "- "
                + message;
    }

    // RFC 3164
    public String format3164(EventGenerator.LogEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event must not be null");
        }

        String timestamp = normalizeTimestamp3164(event.getTimestamp());
        String message = sanitizeMessage(event.getMessage());
        String tag = appName + "[" + procId + "]:";
        int pri = calculatePri(event.getSeverity());

        return "<" + pri + ">"
                + timestamp + " "
                + hostname + " "
                + tag + " "
                + sanitizeHeader(event.getEventType())
                + " - "
                + message;
    }

    private String normalizeTimestamp(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return "-";
        }

        try {
            return Instant.parse(timestamp).toString();
        } catch (DateTimeParseException e) {
            return "-";
        }
    }

    private String normalizeTimestamp3164(String timestamp) {
        try {
            Instant instant = (timestamp == null || timestamp.trim().isEmpty())
                    ? Instant.now()
                    : Instant.parse(timestamp);

            return RFC3164_FORMAT.format(instant);
        } catch (DateTimeParseException e) {
            return RFC3164_FORMAT.format(Instant.now());
        }
    }

    private String sanitizeHeader(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }

        return value.trim().replaceAll("\\s+", "_");
    }

    private String sanitizeMessage(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }

        return value.replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }

    public static void main(String[] args) {
        EventGenerator generator = new EventGenerator();
        SyslogFormatter formatter = new SyslogFormatter();

        for (EventGenerator.LogEvent event : generator.generateEvents(5)) {
            System.out.println(formatter.format(event));
        }
    }
}
