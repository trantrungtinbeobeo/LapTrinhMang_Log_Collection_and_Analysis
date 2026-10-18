package com.Logcollector.server.UI.desktop;

import com.Logcollector.server.Parser.LogEvent;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** UI metadata around the existing parser model. Instances are not mutated after publication. */
record DesktopLog(LogEvent event, long receivedAt, String source, String raw, String eventType, String groupKey) {
    private static final Pattern EVENT = Pattern.compile("(?<![A-Z0-9_])([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)(?![A-Z0-9_])");

    static DesktopLog of(LogEvent original, long time, String source, String raw) {
        String level = value(original.getSeverityLevel()).toUpperCase(Locale.ROOT);
        if (level.equals("WARN")) level = "WARNING";
        if (level.isBlank()) level = "UNKNOWN";
        LogEvent copy = new LogEvent(value(original.getIp()), value(original.getDevice()),
                value(original.getCategory()), value(original.getTimestamp()), original.getFacility(),
                level, value(original.getMessage()));
        String message = copy.getMessage().trim().replaceAll("\\s+", " ");
        Matcher matcher = EVENT.matcher(message);
        String code = "";
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!candidate.equals("UI_TEST")) { code = candidate; break; }
        }
        String title = code.isEmpty() ? copy.getCategory() + " · " + message : code;
        String key = level + "\u0000" + (code.isEmpty() ? copy.getCategory() + "\u0000" + message : code);
        return new DesktopLog(copy, time, source, value(raw), title, key);
    }

    static String value(String text) { return text == null ? "" : text; }
    boolean matches(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return event.getIp().toLowerCase(Locale.ROOT).contains(q)
                || event.getMessage().toLowerCase(Locale.ROOT).contains(q)
                || eventType.toLowerCase(Locale.ROOT).contains(q);
    }
}
