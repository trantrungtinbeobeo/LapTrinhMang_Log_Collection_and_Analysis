package com.Logcollector.server.Parser;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {

    // 1. REGEX: Cấu trúc bóc tách chuẩn Syslog (Có chứa PRI, Timestamp, IP và Nội dung)
    private static final String SYSLOG_REGEX = "^<(\\d{1,3})>(?:(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}|[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}))?\\s+([0-9a-zA-Z.:-]+)\\s+(.*)$";
    private static final Pattern PATTERN = Pattern.compile(SYSLOG_REGEX);

    // Mảng dùng để chuẩn hóa Severity Level (Từ mã số sang tên gọi)
    private static final String[] SEVERITY_NAMES = {
        "EMERGENCY", "ALERT", "CRITICAL", "ERROR", 
        "WARNING", "NOTICE", "INFO", "DEBUG"
    };

    /**
     * Hàm bóc tách (Parse) và chuẩn hóa (Normalize)
     */
    public static LogEvent parse(String rawLog) {
        if (rawLog == null || rawLog.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = PATTERN.matcher(rawLog.trim());

        if (matcher.matches()) {
            // 2. NORMALIZATION (Chuẩn hóa) Priority -> Facility và Severity Level
            int pri = Integer.parseInt(matcher.group(1));
            int facility = pri / 8;
            int severityCode = pri % 8;
            String severityLevel = (severityCode >= 0 && severityCode < SEVERITY_NAMES.length) 
                                    ? SEVERITY_NAMES[severityCode] 
                                    : "UNKNOWN";

            // Chuẩn hóa thời gian (Timestamp)
            String rawTimestamp = matcher.group(2);
            String timestamp = (rawTimestamp != null && !rawTimestamp.isEmpty()) 
                                ? rawTimestamp.trim() 
                                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // Chuẩn hóa IP và loại bỏ khoảng trắng thừa ở Message
            String ip = matcher.group(3).trim();
            String message = matcher.group(4).trim();

            // Trả về đối tượng LogEvent đã chứa dữ liệu sạch
            return new LogEvent(ip, timestamp, facility, severityLevel, message);
        } else {
            return new LogEvent("127.0.0.1", 
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                                1, "INFO", rawLog.trim());
        }
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