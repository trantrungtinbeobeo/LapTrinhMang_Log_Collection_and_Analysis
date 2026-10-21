# LapTrinhMang_Log_Collection_and_Analysis

Hệ thống thu thập, chuẩn hóa, phân tích và cảnh báo bất thường từ dữ liệu log tập trung qua mạng (UDP / TCP / WebSocket).

---

## Bảng Đóng Góp Của Các Thành Viên (README.md)

| MSSV | Họ và Tên | Công việc đã thực hiện | Merge Requests |
| :--- | :--- | :--- | :--- |
| **2380602255** | Trần Trung Tín | Xây dựng Ingestion Engine đa luồng (TCP Receiver cổng 9999, UDP Receiver cổng 514, hàng đợi RAM LogQueue 100.000 log); tích hợp toàn diện ServerMain (Pipeline xử lý luồng, Tường lửa lọc IP BLOCKED_IPS, ngắt mạch quá tải 2.000 log và hàng đợi đệm tràn pendingOverflowLogs); xây dựng hệ thống Server 2 Backup & Cụm mở rộng (kết nối đồng bộ cụm TCP 9997, logs_backup.db, replication thời gian thực); tích hợp tập dữ liệu thực tế OpenSSH_2k và xây dựng bộ kịch bản kiểm thử (Stress test đa luồng, Cyber Attack phát hiện IP thật, User Increase 200 user, Flash Sale Traffic Spike). | !18, !26, !28, !13 |
| **2380612774** | Nguyễn Quốc Hòa | Xây dựng trạm thu thập Client-Agent độc lập: bộ sinh sự kiện mẫu EventGenerator (đối tượng LogEvent đa trường thông tin); chuẩn hóa định dạng nhật ký theo chuẩn quốc tế Syslog RFC 5424, RFC 3164 và JSON (SyslogFormatter, JsonFormatter); hàng đợi đệm an toàn AgentLocalQueue trên RAM máy client chống mất dữ liệu khi mất kết nối mạng; xây dựng bộ truyền tải mạng đa giao thức (TCPSender qua TCP 9999 có timeout, UDPSender qua UDP 514) và ClientAgentMain tự động điều phối giao thức theo mức độ nghiêm trọng (Severity). | !22, !23 |
| **2380601884** | Nguyễn Phạm Quỳnh | Xây dựng bộ bóc tách nhật ký LogParser sử dụng biểu thức chính quy (Regex Syslog) trích xuất IP, thiết bị, phân loại Category và chuẩn hóa thời gian timestamp về định dạng yyyy-MM-dd HH:mm:ss; thiết kế cấu trúc dữ liệu đối tượng LogEvent; xây dựng DatabaseManager kết nối CSDL SQLite (logs.db), triển khai cơ chế ghi hàng loạt Batch Insert (50 log/lần) tối ưu hóa I/O đĩa cứng và API truy vấn, thanh lọc nhật ký vi phạm theo IP. | !15, !16 |
| **2380600837** | Ngô Gia Huy | Xây dựng bộ phát hiện bất thường trung tâm AnomalyDetector và các luật an ninh mạng (PortScanRule phát hiện quét cổng, BruteForceRule theo dõi cửa sổ trượt phát hiện >= 5 lần đăng nhập sai kích hoạt cảnh báo CRITICAL); xây dựng máy chủ WebSocketServer chuẩn RFC 6455 trên cổng 8085 quản lý kết nối đa luồng, phát tán nhật ký thời gian thực và đẩy tín hiệu cảnh báo an ninh xuống giao diện Dashboard. | !14, !17 |
| **2380602004** | Nguyễn Minh Tấn | Xây dựng cơ chế xoay vòng và dọn dẹp log quá hạn (LogRotation); thiết kế Dashboard Web và ứng dụng giao diện Java Desktop Swing chuyên nghiệp (DesktopFrame, DashboardUI): biểu đồ sóng Throughput Wave, biểu đồ tròn phân bổ mức độ nghiêm trọng (Severity), widget giám sát tải Cụm Server (chuyển màu an toàn/cảnh báo/quá tải), bảng hiển thị nhật ký thời gian thực, quản lý Top IP và âm thanh chuông báo động an ninh. | !16, !25, !33 |
