package com.Logcollector.server.Analyzer;

/**
 * Bộ phát hiện bất thường trung tâm (Anomaly Detector):
 * Nhận các sự kiện truy cập/đăng nhập từ hệ thống phân tích log,
 * điều phối kiểm tra qua các luật PortScanRule và BruteForceRule,
 * sau đó kích hoạt cảnh báo nếu phát hiện hành vi xâm nhập.
 */
public class AnomalyDetector {

    private final PortScanRule portScanRule;
    private final BruteForceRule bruteForceRule;

    // Interface callback để gửi cảnh báo tới hệ thống khác (ví dụ: WebSocketServer ở Issue #8)
    public interface AlertListener {
        void onAlertTriggered(String alertType, String sourceIp, String message);
    }

    private AlertListener alertListener;

    public AnomalyDetector() {
        this.portScanRule = new PortScanRule();
        this.bruteForceRule = new BruteForceRule();
    }

    public AnomalyDetector(PortScanRule portScanRule, BruteForceRule bruteForceRule) {
        this.portScanRule = portScanRule;
        this.bruteForceRule = bruteForceRule;
    }

    /**
     * Đăng ký bộ lắng nghe cảnh báo (chuẩn bị kết nối WebSocket ở Issue #8)
     */
    public void setAlertListener(AlertListener listener) {
        this.alertListener = listener;
    }

    /**
     * Xử lý sự kiện truy cập cổng mạng từ một IP
     * @param sourceIp IP nguồn gửi gói tin
     * @param targetPort Cổng đích đang truy cập
     */
    public void processConnectionEvent(String sourceIp, int targetPort) {
        boolean isScanning = portScanRule.checkPortScan(sourceIp, targetPort);
        if (isScanning) {
            String message = String.format("Phat hien hanh vi Port Scan tu IP: %s (dang truy cap cong %d)", sourceIp, targetPort);
            triggerAlert("PORT_SCAN", sourceIp, message);
            // Reset để tránh lặp cảnh báo liên tục
            portScanRule.resetIp(sourceIp);
        }
    }

    /**
     * Xử lý sự kiện đăng nhập hệ thống
     * @param sourceIp IP nguồn thực hiện đăng nhập
     * @param isSuccess Trạng thái đăng nhập (true: thành công, false: thất bại)
     */
    public void processLoginEvent(String sourceIp, boolean isSuccess) {
        if (isSuccess) {
            bruteForceRule.recordLoginSuccess(sourceIp);
            return;
        }

        boolean isBruteForce = bruteForceRule.recordLoginFailure(sourceIp);
        if (isBruteForce) {
            String message = String.format("Phat hien hanh vi Brute-force mat khau tu IP: %s", sourceIp);
            triggerAlert("BRUTE_FORCE", sourceIp, message);
            // Reset để tránh lặp cảnh báo liên tục
            bruteForceRule.resetIp(sourceIp);
        }
    }

    /**
     * Phát cảnh báo ra console và chuyển tiếp tới listener (WebSocket/Database)
     */
    private void triggerAlert(String alertType, String sourceIp, String message) {
        System.err.println("[CANH BAO AN NINH] [" + alertType + "] " + message);
        if (alertListener != null) {
            alertListener.onAlertTriggered(alertType, sourceIp, message);
        }
    }
}