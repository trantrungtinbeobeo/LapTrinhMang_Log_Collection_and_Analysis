package com.Logcollector.server.Analyzer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Quy tắc phát hiện dò mật khẩu (Brute-Force):
 * Giám sát số lần đăng nhập thất bại từ một IP nguồn.
 * Nếu số lần thất bại vượt quá ngưỡng trong khung thời gian quy định,
 * hệ thống sẽ đánh dấu và kích hoạt cảnh báo bất thường.
 */
public class BruteForceRule {

    // Cấu hình ngưỡng mặc định: Quá 5 lần thất bại trong 60 giây (60.000 ms)
    private final int failureThreshold;
    private final long timeWindowMillis;

    // Lưu trữ số lần thử thất bại của từng IP: IP -> Thống kê thất bại
    private final Map<String, FailureRecord> failureHistory = new ConcurrentHashMap<>();

    public BruteForceRule() {
        this(5, 60_000L);
    }

    public BruteForceRule(int failureThreshold, long timeWindowMillis) {
        this.failureThreshold = failureThreshold;
        this.timeWindowMillis = timeWindowMillis;
    }

    /**
     * Ghi nhận một lần đăng nhập thất bại từ IP nguồn.
     * @param sourceIp IP nguồn thực hiện đăng nhập
     * @return true nếu số lần thất bại chạm hoặc vượt ngưỡng cảnh báo, ngược lại false
     */
    public boolean recordLoginFailure(String sourceIp) {
        long currentTime = System.currentTimeMillis();

        FailureRecord record = failureHistory.compute(sourceIp, (ip, existingRecord) -> {
            // Nếu chưa có lịch sử hoặc bản ghi cũ đã quá cửa sổ thời gian, tạo mới
            if (existingRecord == null || (currentTime - existingRecord.startTime > timeWindowMillis)) {
                return new FailureRecord(currentTime);
            }
            // Tăng số lần thử thất bại
            existingRecord.failureCount.incrementAndGet();
            return existingRecord;
        });

        return record.failureCount.get() >= failureThreshold;
    }

    /**
     * Khi IP đăng nhập thành công, ta có thể đặt lại lịch sử của IP này
     * @param sourceIp IP nguồn
     */
    public void recordLoginSuccess(String sourceIp) {
        failureHistory.remove(sourceIp);
    }

    /**
     * Xóa bản ghi sau khi đã cảnh báo để tránh spam cảnh báo
     */
    public void resetIp(String sourceIp) {
        failureHistory.remove(sourceIp);
    }

    // Lớp nội bộ theo dõi thời gian và số lần đăng nhập thất bại
    private static class FailureRecord {
        final long startTime;
        final AtomicInteger failureCount;

        FailureRecord(long startTime) {
            this.startTime = startTime;
            this.failureCount = new AtomicInteger(1);
        }
    }
}