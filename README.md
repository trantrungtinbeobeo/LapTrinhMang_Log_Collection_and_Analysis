# LapTrinhMang_Log_Collection_and_Analysis

Hệ thống thu thập, chuẩn hóa, phân tích và cảnh báo bất thường từ dữ liệu log tập trung qua mạng (UDP / TCP / WebSocket).

---

## Bảng Đóng Góp Của Các Thành Viên (README.md)

| MSSV | Họ và Tên | Công việc đã thực hiện | Merge Requests |
| :--- | :--- | :--- | :--- |
| **2380602255** | Trần Trung Tín | Xây dựng Ingestion Engine đa luồng (TCP/UDP), tích hợp Pipeline xử lý ServerMain, xây dựng bộ kịch bản kiểm thử (Test Cases, Traffic Spike 100-200 user, Cyber Attack) | !18, !26, !28 |
| **2380612774** | Nguyễn Quốc Hòa | Xây dựng Client Agent, sinh log theo chuẩn Syslog RFC 5424/RFC 3164/JSON, hàng đợi cục bộ AgentLocalQueue và cơ chế gửi UDP/TCP có retry | !22, !23 |
| **2380601884** | Nguyễn Phạm Quỳnh | Xây dựng bộ bóc tách LogParser (Regex Syslog), chuẩn hóa đối tượng LogEvent, khởi tạo DatabaseManager lưu trữ log SQLite và cơ chế Data Store (BatchWorker) | !15, !16 |
| **2380600837** | Ngô Gia Huy | Xây dựng bộ phát hiện bất thường AnomalyDetector (Luật dò cổng PortScanRule và dò mật khẩu BruteForceRule), xây dựng WebSocketServer phát tán dữ liệu và cảnh báo thời gian thực | !14, !17 |
| **2380602004** | Nguyễn Minh Tấn | Xây dựng cơ chế xoay vòng và dọn dẹp log quá hạn (LogRotation), thiết kế giao diện Web Dashboard, trang Đăng ký / Đăng nhập và danh mục Sản phẩm | !16, !25 |
