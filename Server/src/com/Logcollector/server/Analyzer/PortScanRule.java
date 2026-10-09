package com.Logcollector.server.Analyzer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quy tắc phát hiện Port Scan:
 * Nếu một IP cố gắng kết nối tới nhiều cổng đích khác nhau vượt quá ngưỡng (threshold)
 * trong cửa sổ thời gian (time window), hệ thống sẽ kích hoạt cảnh báo.
 */
public class PortScanRule {

    // Cấu hình ngưỡng mặc định: > 10 cổng khác nhau trong 60 giây (60000 ms)
    private final int portThreshold;
    private final long timeWindowMillis;

    // Lưu trữ thông tin quét theo từng IP nguồn: IP -> Thông tin quét
    private final Map<String, ScanRecord> scanHistory = new ConcurrentHashMap<>();

    public PortScanRule() {
        this(10, 60_000L);
    }

    public PortScanRule(int portThreshold, long timeWindowMillis) {
        this.portThreshold = portThreshold;
        this.timeWindowMillis = timeWindowMillis;
    }

    /**
     * Ghi nhận một kết nối và kiểm tra có vi phạm quy tắc quét cổng không.
     * @param sourceIp IP nguồn gửi yêu cầu
     * @param targetPort Cổng đích đang cố gắng kết nối
     * @return true nếu phát hiện hành vi quét cổng, ngược lại false
     */
    public boolean checkPortScan(String sourceIp, int targetPort) {
        long currentTime = System.currentTimeMillis();

        ScanRecord record = scanHistory.compute(sourceIp, (ip, existingRecord) -> {
            // Nếu chưa có lịch sử hoặc bản ghi đã quá thời gian timeWindow, làm mới
            if (existingRecord == null || (currentTime - existingRecord.startTime > timeWindowMillis)) {
                return new ScanRecord(currentTime, targetPort);
            }
            // Ngược lại, thêm cổng mới vào danh sách theo dõi
            existingRecord.scannedPorts.add(targetPort);
            return existingRecord;
        });

        // Kiểm tra số lượng cổng phân biệt đã thử nghiệm
        return record.scannedPorts.size() >= portThreshold;
    }

    /**
     * Xóa dữ liệu quét của một IP sau khi đã xử lý/cảnh báo để tránh cảnh báo lặp liên tục
     */
    public void resetIp(String sourceIp) {
        scanHistory.remove(sourceIp);
    }

    // Lớp nội bộ lưu trữ các cổng đích và mốc thời gian bắt đầu quét
    private static class ScanRecord {
        final long startTime;
        final Set<Integer> scannedPorts;

        ScanRecord(long startTime, int initialPort) {
            this.startTime = startTime;
            this.scannedPorts = Collections.newSetFromMap(new ConcurrentHashMap<>());
            this.scannedPorts.add(initialPort);
        }
    }
}