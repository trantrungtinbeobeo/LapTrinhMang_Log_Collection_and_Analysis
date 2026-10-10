package com.Logcollector.server.Parser;

public class LogEvent {
    private String ip;
    private String timestamp;
    private int facility;
    private String severityLevel;
    private String message;

    public LogEvent() {
    }

    public LogEvent(String ip, String timestamp, int facility, String severityLevel, String message) {
        this.ip = ip;
        this.timestamp = timestamp;
        this.facility = facility;
        this.severityLevel = severityLevel;
        this.message = message;
    }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public int getFacility() { return facility; }
    public void setFacility(int facility) { this.facility = facility; }

    public String getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(String severityLevel) { this.severityLevel = severityLevel; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public String toString() {
        return String.format("LogEvent [IP: %s | Timestamp: %s | Facility: %d | Level: %s | Message: %s]",
                ip, timestamp, facility, severityLevel, message);
    }
}