package com.Logcollector.server.Parser;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {

    // 1. REGEX: Cấu trúc bóc tách chuẩn Syslog (Có chứa PRI, Timestamp, IP và Nội dung)
    private static final String SYSLOG_REGEX = "^<(\\d{1,3})>(?:1\\s+)?(?:(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z?|[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}))?\\s+([0-9a-zA-Z.:_-]+)\\s+(.*)$";
    private static final Pattern PATTERN = Pattern.compile(SYSLOG_REGEX);

    // Mảng dùng để chuẩn hóa Severity Level (Từ mã số sang tên gọi)
    private static final String[] SEVERITY_NAMES = {
        "EMERGENCY", "ALERT", "CRITICAL", "ERROR", 
        "WARNING", "NOTICE", "INFO", "DEBUG"
    };

    private static final Pattern DEVICE_PATTERN = Pattern.compile("(?:device|Device|from device)\\s*['\"]([^'\"]+)['\"]|(?:device|Device|from device)\\s*[:=]?\\s*([^'\"|\\r\\n]+)");
    private static final Pattern IP_PATTERN = Pattern.compile("(?:\\b|from\\s+|rhost=|IP:\\s*|from\\s+IP\\s+)([0-9]{1,3}(?:\\.[0-9]{1,3}){3})");

    /**
     * Ham boc tach (Parse) va chuan hoa (Normalize)
     */
    public static LogEvent parse(String rawLog) {
        if (rawLog == null || rawLog.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = PATTERN.matcher(rawLog.trim());

        if (matcher.matches()) {
            // 2. NORMALIZATION (Chuan hoa) Priority -> Facility va Severity Level
            int pri = Integer.parseInt(matcher.group(1));
            int facility = pri / 8;
            int severityCode = pri % 8;
            String severityLevel = (severityCode >= 0 && severityCode < SEVERITY_NAMES.length) 
                                    ? SEVERITY_NAMES[severityCode] 
                                    : "UNKNOWN";

            // Chuan hoa thoi gian (Timestamp)
            String rawTimestamp = matcher.group(2);
            String timestamp = (rawTimestamp != null && !rawTimestamp.isEmpty()) 
                                ? rawTimestamp.trim() 
                                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // Chuan hoa IP, thiet bi va noi dung
            String token = matcher.group(3).trim();
            String message = matcher.group(4).trim();

            String ip = "127.0.0.1";
            String device = "Network-Device";

            if (token.matches("^[0-9]{1,3}(?:\\.[0-9]{1,3}){3}$")) {
                ip = token;
                device = extractDevice(message, null);
            } else if ("0:0:0:0:0:0:0:1".equals(token) || "::1".equals(token) || "localhost".equalsIgnoreCase(token)) {
                ip = "127.0.0.1";
                device = extractDevice(message, null);
            } else {
                device = token.replace("_", " ");
                Matcher ipMatcher = IP_PATTERN.matcher(message);
                if (ipMatcher.find()) {
                    ip = ipMatcher.group(1);
                } else {
                    ip = "127.0.0.1";
                }
                if (!device.contains("[User:") && !device.contains("User:")) {
                    Matcher u = USER_PATTERN.matcher(message);
                    if (u.find()) {
                        String user = u.group(1).trim();
                        if (!user.isEmpty()) {
                            device = device + " [User: " + user + "]";
                        }
                    }
                }
            }

            // Xac dinh nhom phan loai (Category)
            String category = determineCategory(rawLog);

            // Tra ve doi tuong LogEvent da chua du lieu day du
            return new LogEvent(ip, device, category, timestamp, facility, severityLevel, message);
        } else {
            String category = determineCategory(rawLog);
            String device = extractDevice(rawLog, "Network-Host");
            Matcher ipMatcher = IP_PATTERN.matcher(rawLog);
            String ip = ipMatcher.find() ? ipMatcher.group(1) : "127.0.0.1";

            return new LogEvent(ip, device, category, 
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                                1, "INFO", rawLog.trim());
        }
    }

    public static String determineCategory(String text) {
        if (text == null) return "APPLICATION";
        String upper = text.toUpperCase();
        if (upper.contains("PORT_SCAN") || upper.contains("BRUTE_FORCE") || upper.contains("ATTACK") || upper.contains("FIREWALL") || upper.contains("BLOCKED")) {
            return "SECURITY";
        }
        if (upper.contains("LOGIN") || upper.contains("PASSWORD") || upper.contains("USER") || upper.contains("AUTHENTICAT") || upper.contains("SSH") || upper.contains("LOGOUT")) {
            return "AUTH";
        }
        if (upper.contains("TRAFFIC") || upper.contains("SPIKE") || upper.contains("LATENCY") || upper.contains("BANDWIDTH") || upper.contains("TELEMETRY") || upper.contains("THROUGHPUT")) {
            return "NETWORK";
        }
        if (upper.contains("CPU") || upper.contains("MEMORY") || upper.contains("DISK") || upper.contains("FAN") || upper.contains("POWER") || upper.contains("REBOOT") || upper.contains("INTERFACE")) {
            return "SYSTEM";
        }
        return "APPLICATION";
    }

    private static final Pattern USER_PATTERN = Pattern.compile("(?:User|user|for user)\\s*['\"]?([a-zA-Z0-9_.-]+)['\"]?");

    public static String extractDevice(String rawLog, String fallback) {
        if (rawLog != null) {
            Matcher m = DEVICE_PATTERN.matcher(rawLog);
            if (m.find()) {
                String dev = m.group(1) != null ? m.group(1).trim() : (m.group(2) != null ? m.group(2).trim() : "");
                if (!dev.isEmpty()) {
                    if (!dev.contains("[User:") && !dev.contains("User:")) {
                        Matcher u = USER_PATTERN.matcher(rawLog);
                        if (u.find()) {
                            String user = u.group(1).trim();
                            if (!user.isEmpty()) {
                                return dev + " [User: " + user + "]";
                            }
                        }
                    }
                    return dev;
                }
            }
            Matcher u = USER_PATTERN.matcher(rawLog);
            if (u.find()) {
                String user = u.group(1).trim();
                if (!user.isEmpty()) {
                    String base = (fallback != null && !fallback.trim().isEmpty()) ? fallback : "Network-Device";
                    return base + " [User: " + user + "]";
                }
            }
        }
        return (fallback != null && !fallback.trim().isEmpty()) ? fallback : "Network-Device";
    }

    /**
     * Hàm Main để chạy thử nghiệm và in kết quả ra Terminal chụp ảnh nộp bài
     */
    public static void main(String[] args) {
        // Chuỗi log giả lập để test
        String[] sampleLogs = {
            "<131>2026-10-09 13:45:00 192.168.1.105 Failed password for invalid user admin",
            "<132>2026-10-09 13:46:12 10.0.0.15 High CPU utilization detected: 94%",
            "<134>2026-10-09 13:47:30 172.16.0.2 User root successfully logged in via SSH"
        };

        System.out.println("================= KET QUA BOC TACH LOG (ISSUE #4) =================");
        for (int i = 0; i < sampleLogs.length; i++) {
            System.out.println("\n[RAW LOG " + (i + 1) + "]: " + sampleLogs[i]);
            LogEvent event = LogParser.parse(sampleLogs[i]);

            if (event != null) {
                System.out.println("-> IP Nguon   : " + event.getIp());
                System.out.println("-> Timestamp  : " + event.getTimestamp());
                System.out.println("-> Facility   : " + event.getFacility());
                System.out.println("-> Level      : " + event.getSeverityLevel());
                System.out.println("-> Message    : " + event.getMessage());
            }
        }
        System.out.println("\n===================================================================");
    }
}