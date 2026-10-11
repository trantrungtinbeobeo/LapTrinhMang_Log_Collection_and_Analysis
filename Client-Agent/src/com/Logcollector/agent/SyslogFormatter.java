
package com.Logcollector.agent;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Dinh dang su kien thanh thong diep Syslog theo RFC 5424.
 */
public class SyslogFormatter {

    private static final int VERSION = 1;
    private static final int FACILITY = 1; // USER-level messages

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

    /**
     * Chuyen severity thanh Syslog Severity Number.
     * 0 = Emergency, 7 = Debug.
     */
    private int toSyslogSeverity(String severity) {
        if (severity == null) {
            return 5; // Notice
        }

        switch (severity.toUpperCase()) {
            case "CRITICAL":
                return 2; // Critical
            case "ERROR":
                return 3; // Error
            case "WARNING":
                return 4; // Warning
            case "INFO":
                return 6; // Informational
            default:
                return 5; // Notice
        }
    }

    /**
     * Tao PRI = Facility * 8 + Severity.
     */
    private int calculatePri(String severity) {
        return FACILITY * 8 + toSyslogSeverity(severity);
    }

    /**
     * Dinh dang su kien theo RFC 5424:
     * <PRI>VERSION TIMESTAMP HOSTNAME APP-NAME PROCID MSGID SD MSG
     */
    public String format(EventGenerator.LogEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event must not be null");
        }

        String timestamp = normalizeTimestamp(event.getTimestamp());
        String eventType = sanitizeHeader(event.getEventType());
        String message = event.getMessage() == null
                ? "-" : event.getMessage();

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

        for (EventGenerator.LogEvent event : generator.generateEvents(5)) {
            System.out.println(formatter.format(event));
        }
    }
}
