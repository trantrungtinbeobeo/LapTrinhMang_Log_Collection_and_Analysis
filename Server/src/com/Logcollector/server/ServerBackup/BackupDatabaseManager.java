package com.Logcollector.server.ServerBackup;

import com.Logcollector.server.Parser.LogEvent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

/**
 * Quan ly CSDL SQLite rieng cho Server 2 (ServerBackup) voi tap tin logs_backup.db.
 * Tach biet hoan toan voi logs.db cua Server 1 de tranh xung dot khoa tep khi chay tren cung may.
 */
public class BackupDatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:logs_backup.db";

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout=5000;");
        }
        return conn;
    }

    public static void initializeDatabase() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS backup_logs ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "ip_address TEXT, "
                + "timestamp TEXT, "
                + "facility INTEGER, "
                + "severity_level TEXT, "
                + "message TEXT, "
                + "synced_at TEXT"
                + ");";

        String createIndexSQL = "CREATE INDEX IF NOT EXISTS idx_backup_logs_ip ON backup_logs (ip_address);";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute(createTableSQL);
            stmt.execute(createIndexSQL);
            System.out.println("[BackupDatabase] Da khoi tao CSDL logs_backup.db danh rieng cho Server 2.");
        } catch (SQLException e) {
            System.err.println("[BackupDatabase] Loi khoi tao CSDL Server 2: " + e.getMessage());
        }
    }

    public static void insertLogBatch(List<LogEvent> logBatch) {
        if (logBatch == null || logBatch.isEmpty()) return;

        String insertSQL = "INSERT INTO backup_logs (ip_address, timestamp, facility, severity_level, message, synced_at) VALUES (?, ?, ?, ?, ?, ?)";
        String now = Instant.now().toString();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            conn.setAutoCommit(false);

            for (LogEvent event : logBatch) {
                pstmt.setString(1, event.getIp());
                pstmt.setString(2, event.getTimestamp());
                pstmt.setInt(3, event.getFacility());
                pstmt.setString(4, event.getSeverityLevel());
                pstmt.setString(5, event.getMessage());
                pstmt.setString(6, now);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            conn.commit();
            System.out.println("[BackupDatabase] Da dong bo thanh cong lo " + logBatch.size() + " logs vao CSDL Server 2.");

        } catch (SQLException e) {
            System.err.println("[BackupDatabase] Loi ghi Batch vao Server 2: " + e.getMessage());
        }
    }

    public static void insertSingleLog(LogEvent event) {
        if (event == null) return;
        String insertSQL = "INSERT INTO backup_logs (ip_address, timestamp, facility, severity_level, message, synced_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, event.getIp());
            pstmt.setString(2, event.getTimestamp());
            pstmt.setInt(3, event.getFacility());
            pstmt.setString(4, event.getSeverityLevel());
            pstmt.setString(5, event.getMessage());
            pstmt.setString(6, Instant.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[BackupDatabase] Loi ghi log don le vao Server 2: " + e.getMessage());
        }
    }

    public static int getTotalLogCount() {
        String sql = "SELECT COUNT(*) FROM backup_logs";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[BackupDatabase] Loi dem log Server 2: " + e.getMessage());
        }
        return 0;
    }
}
