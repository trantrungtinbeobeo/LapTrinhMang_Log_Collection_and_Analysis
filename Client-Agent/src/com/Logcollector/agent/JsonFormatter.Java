package com.Logcollector.agent;

/**
 * Dinh dang su kien thanh JSON cho Client Agent.
 */
public class JsonFormatter {

    public String format(EventGenerator.LogEvent event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Event must not be null"
            );
        }

        return "{"
                + "\"timestamp\":\"" + escapeJson(event.getTimestamp()) + "\","
                + "\"severity\":\"" + escapeJson(event.getSeverity()) + "\","
                + "\"event_type\":\"" + escapeJson(event.getEventType()) + "\","
                + "\"message\":\"" + escapeJson(event.getMessage()) + "\""
                + "}";
    }

    /**
     * Xu ly cac ky tu dac biet de tao JSON hop le.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (char c : value.toCharArray()) {
            switch (c) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
            }
        }

        return result.toString();
    }

    public static void main(String[] args) {
        EventGenerator generator = new EventGenerator();
        JsonFormatter formatter = new JsonFormatter();

        for (EventGenerator.LogEvent event
                : generator.generateEvents(5)) {
            System.out.println(formatter.format(event));
        }
    }
}