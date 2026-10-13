# UI — phần 1: đăng ký và đăng nhập demo

Frontend HTML/CSS/JavaScript thuần, chuyển từ bản Stitch do nhóm cung cấp. Tất cả thay đổi nằm trong thư mục `UI`; `DashboardUI.java` và `ChartManager.java` được giữ nguyên. Không thêm backend, dependency, công cụ build hoặc cấu hình dự án.

## Các file của commit này

- `login.html`, `register.html`: cấu trúc hai màn hình.
- `css/common.css`: màu sắc và các thành phần dùng chung.
- `css/auth.css`: bố cục và trạng thái của form tài khoản.
- `js/mock-data.js`: tài khoản mẫu, phiên demo và tối đa 500 sự kiện mô phỏng trong `sessionStorage`.
- `js/ui-common.js`: lỗi trường nhập, hiện/ẩn mật khẩu và thông báo khi không lưu được phiên.
- `js/login.js`, `js/register.js`: tương tác riêng của từng màn hình.
- `assets/images/logo.svg`: logo cục bộ, không cần CDN.

## Xem giao diện khi muốn kiểm tra thủ công

Mở `login.html` bằng trình duyệt hiện đại. CSS, JavaScript và logo đều dùng đường dẫn tương đối, không cần Java server, npm, Tailwind CDN hoặc font mạng. Khi chưa có Inter trên máy, giao diện dùng Segoe UI/Arial.

Tài khoản mẫu công khai: `minhan@demostore.vn` / `Demo123!`. Có thể chọn “Trải nghiệm khách hàng” để bỏ qua việc nhập tài khoản demo. Đăng nhập thành công hiển thị phiên demo ngay trên trang; có nút đăng xuất. Nút dashboard tạm vô hiệu hóa để không dẫn tới trang chưa được triển khai.

Đăng ký chỉ kiểm tra form và mô phỏng thành công. Không tạo tài khoản thật, không sử dụng mật khẩu vừa đăng ký để đăng nhập. Sau đăng ký, dùng liên kết trong thông báo thành công để tới trang đăng nhập với tài khoản mẫu. Email `demo@example.com` và email tài khoản mẫu được dành cho tình huống trùng lặp.

Không lưu mật khẩu vào storage hoặc log. Phiên chỉ mang thông tin khách hàng mẫu; nhật ký chỉ chứa loại sự kiện, thời gian, severity và thông điệp cố định. Đây không phải hệ thống xác thực hoặc phân quyền. Không gọi API, WebSocket hoặc database.

Với `file://`, chính sách chia sẻ `sessionStorage` giữa các trang tùy trình duyệt. Nếu storage bị chặn, form vẫn hoạt động trong trang hiện tại và có thông báo giới hạn. Muốn kiểm tra liên trang ổn định về sau có thể dùng cùng một nguồn localhost; không cần thay đổi Java backend. Lượt triển khai này chưa khởi chạy trình duyệt, server, build hoặc test.

## Các tình huống kiểm tra thủ công (chưa thực thi)

1. Đăng nhập với tài khoản mẫu: có thông báo thành công, mật khẩu bị xóa khỏi form; đăng xuất quay về trạng thái nhập.
2. Email sai định dạng hoặc mật khẩu trống: lỗi ở đúng trường, không chuyển trang.
3. Mật khẩu sai: thông báo thất bại, không tạo phiên demo.
4. Đăng ký thiếu tên, email không hợp lệ, mật khẩu dưới 6 ký tự hoặc xác nhận không khớp: không hoàn tất form.
5. Chọn “Lỗi trùng lặp”: điền sẵn email dùng để thử lỗi; gửi form vẫn bị từ chối.
6. Chọn “Thành công”: điền dữ liệu mẫu; chỉ khi gửi form hợp lệ mới ghi nhận một sự kiện đăng ký. Gửi liên tiếp trong lúc xử lý không nhân đôi sự kiện.
7. Đổi hiện/ẩn mật khẩu, điều hướng bằng bàn phím, xem trên màn hình hẹp.
8. Tắt quyền lưu storage: có thông báo, form vẫn cho thử kết quả.

## Phần tiếp theo, chưa triển khai trong commit này

2. Trang sản phẩm, tìm kiếm/lọc và hộp thoại chi tiết.
3. Dashboard, biểu đồ, bảng log và liên kết sự kiện demo.

Các phần tiếp theo chỉ được triển khai ở lượt sau để nhóm kiểm tra và commit từng phần.
