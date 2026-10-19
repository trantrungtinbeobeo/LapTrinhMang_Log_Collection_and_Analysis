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
     * Lay ket noi SQLite an toan voi timeout chong khoa CSDL (busy_timeout)
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout=5000;");
        }
        return conn;
    }

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

        String createIndexSQL = "CREATE INDEX IF NOT EXISTS idx_system_logs_ip ON system_logs (ip_address);";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute(createTableSQL);
            stmt.execute(createIndexSQL);
            System.out.println("[Database] Da khoi tao CSDL, bang system_logs va chi muc IP (SQLite WAL).");
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

        try (Connection conn = getConnection();
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

    /**
     * Xoa toan bo log cua dia chi IP bi chan khoi CSDL nham dam bao tinh toan ven
     */
    public static int purgeLogsByIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) return 0;
        String deleteSQL = "DELETE FROM system_logs WHERE ip_address = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, ip.trim());
            int affected = pstmt.executeUpdate();
            System.out.println("[Database] Da thanh loc xoa " + affected + " log doc hai cua IP bi chan: " + ip);
            return affected;
        } catch (SQLException e) {
            System.err.println("[Database] Loi thanh loc log IP: " + e.getMessage());
            return 0;
        }
    }
}