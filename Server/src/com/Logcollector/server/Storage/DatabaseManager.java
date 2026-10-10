package com.Logcollector.server.Storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import com.Logcollector.server.Parser.LogEvent;

public class DatabaseManager {
    // URL kết nối SQLite (sẽ tự tạo file logs.db trong thư mục dự án)
    private static final String DB_URL = "jdbc:sqlite:logs.db";

    /**
     * Khởi tạo bảng CSDL lưu log sự kiện
     */
    public static void initializeDatabase() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS system_logs ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "ip_address TEXT, "
                + "timestamp TEXT, "
                + "facility INTEGER, "
                + "severity_level TEXT, "
                + "message TEXT"
                + ");";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("[Database] Da khoi tao CSDL va bang system_logs (SQLite).");
        } catch (SQLException e) {
            System.err.println("[Database] Loi khoi tao CSDL: " + e.getMessage());
        }
    }

    /**
     * Cơ chế Batch Insert: Gom nhiều log ghi cùng lúc để tối ưu I/O
     */
    public static void insertLogBatch(List<LogEvent> logBatch) {
        if (logBatch == null || logBatch.isEmpty()) return;

        String insertSQL = "INSERT INTO system_logs (ip_address, timestamp, facility, severity_level, message) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            // Tắt auto-commit để đóng gói toàn bộ lệnh ghi vào một giao dịch (Transaction)
            conn.setAutoCommit(false);

            for (LogEvent event : logBatch) {
                pstmt.setString(1, event.getIp());
                pstmt.setString(2, event.getTimestamp());
                pstmt.setInt(3, event.getFacility());
                pstmt.setString(4, event.getSeverityLevel());
                pstmt.setString(5, event.getMessage());
                pstmt.addBatch(); // Đưa lệnh vào lô chờ
            }

            // Thực thi ghi toàn bộ lô xuống ổ cứng
            pstmt.executeBatch();
            conn.commit(); 

            System.out.println("[Database] Da ghi thanh cong lô gom " + logBatch.size() + " logs xuong CSDL.");

        } catch (SQLException e) {
            System.err.println("[Database] Loi ghi Batch Insert: " + e.getMessage());
        }
    }
}