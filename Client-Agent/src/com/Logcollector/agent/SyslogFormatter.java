
package com.Logcollector.agent;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class SyslogFormatter {

    private static final int FACILITY = 1; // user-level messages

    
    public String format(String timestamp, String severity,
                         String eventType, String message) {
        int severityCode = getSeverityCode(severity);
        int priority = FACILITY * 8 + severityCode;

        String hostname = "client-agent";
        String appName = "LogCollector";

        String safeMessage = message == null ? "" : message;
        safeMessage = safeMessage.replace("\n", " ")
                                 .replace("\r", " ");

        return "<" + priority + ">1 "
                + timestamp + " "
                + hostname + " "
                + appName + " "
                + eventType + " "
                + "- - "
                + safeMessage;
    }


    public String format(String severity, String eventType, String message) {
        int severityCode = getSeverityCode(severity);
        int priority = FACILITY * 8 + severityCode;

        String timestamp = DateTimeFormatter.ISO_INSTANT
                .format(Instant.now());

        String hostname = "client-agent";
        String appName = "LogCollector";

        String safeMessage = message == null ? "" : message;
        safeMessage = safeMessage.replace("\n", " ")
                                 .replace("\r", " ");

        return "<" + priority + ">1 "
                + timestamp + " "
                + hostname + " "
                + appName + " "
                + eventType + " "
                + "- - "
                + safeMessage;
    }

    private int getSeverityCode(String severity) {
        switch (severity.toUpperCase()) {
            case "INFO":
                return 6;
            case "WARNING":
                return 4;
            case "ERROR":
                return 3;
            case "CRITICAL":
                return 2;
            default:
                return 5; // Notice
        }
    }

    public static void main(String[] args) {
        SyslogFormatter formatter = new SyslogFormatter();

        String log = formatter.format(
                "WARNING",
                "LOGIN_FAILED",
                "Simulated login failure"
        );

        System.out.println(log);
    }
}
