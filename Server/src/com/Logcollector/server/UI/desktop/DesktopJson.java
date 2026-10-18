package com.Logcollector.server.UI.desktop;

import java.util.*;

/** Small JSON reader for the existing server protocol; no regex extraction or external dependency. */
final class DesktopJson {
    private final String text;
    private int pos;
    private DesktopJson(String text) { this.text = text; }
    static Map<String, Object> object(String text) {
        DesktopJson parser = new DesktopJson(text);
        Object value = parser.read(0);
        parser.space();
        if (parser.pos != text.length() || !(value instanceof Map<?, ?>)) throw new IllegalArgumentException("JSON object expected");
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }
    static String string(Map<String, Object> map, String key) { Object v = map.get(key); return v == null ? "" : v.toString(); }
    private void space() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++; }
    private char take() { if (pos >= text.length()) throw new IllegalArgumentException("Truncated JSON"); return text.charAt(pos++); }
    private void expect(char c) { space(); if (take() != c) throw new IllegalArgumentException("Expected " + c); }
    private Object read(int depth) {
        if (depth > 24) throw new IllegalArgumentException("JSON too deep");
        space();
        if (pos >= text.length()) throw new IllegalArgumentException("Empty JSON");
        char c = text.charAt(pos);
        if (c == '"') return quoted();
        if (c == '{') {
            pos++; Map<String, Object> map = new LinkedHashMap<>(); space();
            if (pos < text.length() && text.charAt(pos) == '}') { pos++; return map; }
            do { space(); String key = quoted(); expect(':'); map.put(key, read(depth + 1)); space(); c = take(); }
            while (c == ',');
            if (c != '}') throw new IllegalArgumentException("Unclosed object");
            return map;
        }
        if (c == '[') {
            pos++; List<Object> values = new ArrayList<>(); space();
            if (pos < text.length() && text.charAt(pos) == ']') { pos++; return values; }
            do { values.add(read(depth + 1)); space(); c = take(); } while (c == ',');
            if (c != ']') throw new IllegalArgumentException("Unclosed array");
            return values;
        }
        int start = pos;
        while (pos < text.length() && ",]} \r\n\t".indexOf(text.charAt(pos)) < 0) pos++;
        String token = text.substring(start, pos);
        return switch (token) {
            case "true" -> true; case "false" -> false; case "null" -> null;
            default -> {
                if (!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException("Invalid JSON value");
                yield new java.math.BigDecimal(token);
            }
        };
    }
    private String quoted() {
        if (take() != '"') throw new IllegalArgumentException("String expected");
        StringBuilder result = new StringBuilder();
        while (true) {
            char c = take();
            if (c == '"') return result.toString();
            if (c < 32) throw new IllegalArgumentException("Unescaped control character");
            if (c != '\\') { result.append(c); continue; }
            c = take();
            switch (c) {
                case '"', '\\', '/' -> result.append(c);
                case 'b' -> result.append('\b'); case 'f' -> result.append('\f');
                case 'n' -> result.append('\n'); case 'r' -> result.append('\r'); case 't' -> result.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) throw new IllegalArgumentException("Truncated unicode escape");
                    result.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16)); pos += 4;
                }
                default -> throw new IllegalArgumentException("Invalid escape");
            }
        }
    }
}
