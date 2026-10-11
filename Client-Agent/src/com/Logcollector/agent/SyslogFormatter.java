package com.Logcollector.agent;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Dinh dang su kien thanh thong diep Syslog theo RFC 5424.
 */
public class SyslogFormatter {

    private static final int VERSION = 1;
    private static final int FACILITY = 1;

    private final String hostname;
    private final String appName;
    private final String procId;

    public SyslogFormatter() {
        this(
            "client-agent",
            "LogCollector",
            String.valueOf(ProcessHandle.current().pid())
        );
    }

    public SyslogFormatter(
            String hostname,
            String appName,
            String procId) {
        this.hostname = sanitizeHeader(hostname);
        this.appName = sanitizeHeader(appName);
        this.procId = sanitizeHeader(procId);
    }

    /**
     * Chuyen muc do su kien sang Syslog Severity Number.
     */
    private int toSyslogSeverity(String severity) {
        if (severity == null) {
            return 5;
        }

        switch (severity.toUpperCase()) {
            case "CRITICAL":
                return 2;
            case "ERROR":
                return 3;
            case "WARNING":
                return 4;
            case "INFO":
                return 6;
            default:
                return 5;
        }
    }

    /**
     * PRI = Facility * 8 + Severity.
     */
    private int calculatePri(String severity) {
        return FACILITY * 8 + toSyslogSeverity(severity);
    }

    /**
     * RFC 5424:
     * <PRI>VERSION TIMESTAMP HOSTNAME APP-NAME PROCID MSGID SD MSG
     */
    public String format(EventGenerator.LogEvent event) {
        if (event == null) {
            throw new IllegalArgumentException(
                "Event must not be null"
            );
        }

        String timestamp = normalizeTimestamp(
            event.getTimestamp()
        );

        String eventType = sanitizeHeader(
            event.getEventType()
        );

        String message = event.getMessage() == null
                || event.getMessage().isEmpty()
                ? "-"
                : event.getMessage()
                    .replace("\r", " ")
                    .replace("\n", " ");

        int pri = calculatePri(event.getSeverity());

        return "<" + pri + ">" + VERSION + " "
                + timestamp + " "
                + hostname + " "
                + appName + " "
                + procId + " "
                + eventType + " "
                + "- "
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

    /**
     * Header fields khong duoc chua khoang trang.
     */
    private String sanitizeHeader(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }

        return value.trim().replaceAll("\\s+", "_");
    }

    public static void main(String[] args) {
        EventGenerator generator = new EventGenerator();
        SyslogFormatter formatter = new SyslogFormatter();

        for (EventGenerator.LogEvent event
                : generator.generateEvents(5)) {
            System.out.println(formatter.format(event));
        }
    }
}
