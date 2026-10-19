package com.Logcollector.server.UI.desktop;

import java.util.List;

final class DesktopCatalog {
    record Product(String id, String name, String category, long price, String description) {}
    static final List<Product> PRODUCTS = List.of(
        new Product("PRD-LAP-01", "Laptop MacBook Pro M3 Max 14 inch", "Laptop", 49990000L, "Chip Apple Silicon M3 Max / 36GB Unified Memory / 1TB SSD NVMe siêu tốc / Màn hình Liquid Retina XDR 14.2 inch 120Hz ProMotion / Thời lượng pin 18 tiếng."),
        new Product("PRD-LAP-02", "Laptop Gaming Titan X15 (RTX 4070)", "Laptop", 34500000L, "Intel Core i9-14900HX / 32GB DDR5 5600MHz / 1TB NVMe Gen4 / NVIDIA GeForce RTX 4070 8GB GDDR6 / Màn hình 15.6 inch QHD 240Hz / Hệ thống tản nhiệt buồng hơi."),
        new Product("PRD-PHN-01", "iPhone 16 Pro Max 256GB Titanium", "Điện thoại", 34990000L, "Khung viền Titan cấp 5 siêu nhẹ / Chip Apple A18 Pro tiến trình 3nm / Màn hình Super Retina XDR 6.9 inch ProMotion 120Hz / Camera 48MP Zoom quang 5x / Nút Camera Control."),
        new Product("PRD-PHN-02", "Smartphone Horizon 15 Ultra 5G", "Điện thoại", 22490000L, "Khung gốm Ceramic Titan / Snapdragon 8 Gen 3 Extreme / 16GB RAM + 512GB ROM / Cảm biến Sony LYT-900 1 inch 200MP / Pin 5400mAh sạc siêu nhanh 100W."),
        new Product("PRD-ACC-01", "Tai nghe chống ồn Sony WH-1000XM5", "Phụ kiện", 7990000L, "Công nghệ chống ồn chủ động kép Dual-ANC tích hợp bộ xử lý V1 / Driver 30mm màng carbon nhẹ / Âm thanh Hi-Res Audio Wireless LDAC / Thời lượng pin 30 giờ."),
        new Product("PRD-ACC-02", "Chuột không dây Logitech MX Master 3S", "Phụ kiện", 2490000L, "Cảm biến 8000 DPI Darkfield lướt trên mọi bề mặt / Phím bấm Quiet Click giảm 90% tiếng ồn / Con lăn MagSpeed điện từ cuộn 1000 dòng/giây / Kết nối Bluetooth & Logi Bolt."),
        new Product("PRD-ACC-03", "Bàn phím cơ SilentWave K8 Tri-Mode", "Phụ kiện", 2850000L, "Layout 75% gọn gàng / Switch cơ học Silent Linear êm ái triệt tiêu tiếng gõ / Gasket mount cấu trúc đệm cao cấp / Keycap PBT doubleshot bền bỉ / Kết nối Tri-mode 2.4G & Bluetooth 5.3."),
        new Product("PRD-ACC-04", "Màn hình RetinaView 27 inch 4K IPS", "Phụ kiện", 12900000L, "Độ phân giải 3840 x 2160 IPS Ultra HD / Chuẩn màu 99% DCI-P3, HDR600 chuyên đồ họa / Cổng kết nối Thunderbolt 4 truyền hình ảnh và sạc 90W PD / Chân đế nhôm nguyên khối.")
    );
}
