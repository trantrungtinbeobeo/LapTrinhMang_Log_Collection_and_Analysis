package com.Logcollector.server.Parser;

public class LogEvent {
    private String ip;
    private String device;
    private String category;
    private String timestamp;
    private int facility;
    private String severityLevel;
    private String message;

    public LogEvent() {
        this.device = "Default-Device";
        this.category = "APPLICATION";
    }

    public LogEvent(String ip, String timestamp, int facility, String severityLevel, String message) {
        this(ip, "Default-Device", "APPLICATION", timestamp, facility, severityLevel, message);
    }

    public LogEvent(String ip, String device, String category, String timestamp, int facility, String severityLevel, String message) {
        this.ip = ip;
        this.device = (device != null && !device.trim().isEmpty()) ? device : "Default-Device";
        this.category = (category != null && !category.trim().isEmpty()) ? category : "APPLICATION";
        this.timestamp = timestamp;
        this.facility = facility;
        this.severityLevel = severityLevel;
        this.message = message;
    }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

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
        return String.format("LogEvent [IP: %s | Device: %s | Cat: %s | Timestamp: %s | Facility: %d | Level: %s | Message: %s]",
                ip, device, category, timestamp, facility, severityLevel, message);
    }
}