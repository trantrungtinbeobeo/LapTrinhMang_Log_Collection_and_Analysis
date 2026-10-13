# UI — đăng ký, đăng nhập và cửa hàng demo

Frontend HTML/CSS/JavaScript thuần, chuyển từ bản Stitch do nhóm cung cấp. Tất cả thay đổi nằm trong thư mục `UI`; `DashboardUI.java` và `ChartManager.java` được giữ nguyên. Không thêm backend, dependency, công cụ build hoặc cấu hình dự án.

## Phần 1 — đăng ký và đăng nhập

- `login.html`, `register.html`: cấu trúc hai màn hình.
- `css/common.css`: màu sắc và các thành phần dùng chung.
- `css/auth.css`: bố cục và trạng thái của form tài khoản.
- `js/mock-data.js`: tài khoản mẫu, phiên demo và tối đa 500 sự kiện mô phỏng trong `sessionStorage`.
- `js/ui-common.js`: lỗi trường nhập, hiện/ẩn mật khẩu và thông báo khi không lưu được phiên.
- `js/login.js`, `js/register.js`: tương tác riêng của từng màn hình.
- `assets/images/logo.svg`: logo cục bộ, không cần CDN.

## Xem giao diện khi muốn kiểm tra thủ công

Mở `login.html` bằng trình duyệt hiện đại. CSS, JavaScript và logo đều dùng đường dẫn tương đối, không cần Java server, npm, Tailwind CDN hoặc font mạng. Khi chưa có Inter trên máy, giao diện dùng Segoe UI/Arial. Riêng ảnh sản phẩm giữ URL của Stitch nên cần internet; khi tải thất bại, trang dùng ảnh thay thế SVG cục bộ.

Tài khoản mẫu công khai: `minhan@demostore.vn` / `Demo123!`. Có thể chọn “Trải nghiệm khách hàng” để bỏ qua việc nhập tài khoản demo. Đăng nhập thành công hoặc chọn lối tắt sẽ chuyển tới `products.html`. Nếu quay lại trang đăng nhập khi phiên còn tồn tại, có liên kết “Xem sản phẩm” và nút đăng xuất. Nút dashboard tạm vô hiệu hóa để không dẫn tới trang chưa được triển khai.

Đăng ký chỉ kiểm tra form và mô phỏng thành công. Không tạo tài khoản thật, không sử dụng mật khẩu vừa đăng ký để đăng nhập. Sau đăng ký, dùng liên kết trong thông báo thành công để tới trang đăng nhập với tài khoản mẫu. Email `demo@example.com` và email tài khoản mẫu được dành cho tình huống trùng lặp.

Không lưu mật khẩu vào storage hoặc log. Phiên chỉ mang thông tin khách hàng mẫu; nhật ký chứa loại sự kiện, thời gian, severity và thông điệp cố định. Sự kiện `PRODUCT_CLICK` có thêm mã/tên sản phẩm và người dùng mẫu hoặc “Khách demo”. Đây không phải hệ thống xác thực hoặc phân quyền. Không gọi API, WebSocket hoặc database.

## Phần 2 — trang sản phẩm

- `products.html`: header, tìm kiếm, danh mục, lưới sản phẩm, hộp thoại chi tiết và menu tài khoản.
- `css/products.css`: bố cục cửa hàng đáp ứng kích thước màn hình, kiểu thẻ sản phẩm và hộp thoại.
- `js/products-data.js`: tám sản phẩm lấy từ HTML Stitch được cung cấp, giữ tên, mã, giá, mô tả và URL ảnh gốc; không thêm sản phẩm hoặc tải thư viện.
- `js/products.js`: lọc theo tên/mã/danh mục (hỗ trợ từ khóa không dấu), mở/đóng chi tiết, khôi phục focus và ghi nhận lượt nhấn.
- `assets/images/product-placeholder.svg`: ảnh thay thế khi ảnh từ Stitch không tải được.
- Cập nhật `login.html`, `js/login.js` để nối đăng nhập → sản phẩm; cập nhật `js/mock-data.js` để lưu và đọc sự kiện sản phẩm.

Mỗi lần nhấn ảnh, tên hoặc nút chi tiết chỉ ghi nhận một `PRODUCT_CLICK`. Lọc, tìm kiếm, đóng hộp thoại và tải lại trang không tạo lượt nhấn sản phẩm. Hộp thoại đóng bằng nút đóng, nút tiếp tục, Escape hoặc nhấn vùng nền ngoài hộp thoại. Đóng xong trả focus về nút vừa mở.

“Lượt nhấn đã lưu” đếm `PRODUCT_CLICK` trong bộ đệm tối đa 500 sự kiện; không phải tổng vô hạn hoặc dữ liệu từ server. Mở trực tiếp trang sản phẩm khi chưa có phiên hiển thị “Khách demo”; có thể đăng nhập từ menu tài khoản. Đăng xuất xóa phiên demo rồi quay về trang đăng nhập, giữ nhật ký đã có để phục vụ phần dashboard sau.

Không có giỏ hàng, thanh toán, quản lý đơn hàng, đo latency hoặc kết nối collector thật. Phần 3 dashboard chưa được triển khai.

Với `file://`, chính sách chia sẻ `sessionStorage` giữa các trang tùy trình duyệt. Nếu storage bị chặn, form vẫn hoạt động trong trang hiện tại và có thông báo giới hạn. Muốn kiểm tra liên trang ổn định về sau có thể dùng cùng một nguồn localhost; không cần thay đổi Java backend. Lượt triển khai này chưa khởi chạy trình duyệt, server, build hoặc test.

## Các tình huống kiểm tra thủ công (chưa thực thi)

1. Đăng nhập với tài khoản mẫu: mật khẩu bị xóa khỏi form và chuyển sang trang sản phẩm; đăng xuất quay về trang đăng nhập.
2. Email sai định dạng hoặc mật khẩu trống: lỗi ở đúng trường, không chuyển trang.
3. Mật khẩu sai: thông báo thất bại, không tạo phiên demo.
4. Đăng ký thiếu tên, email không hợp lệ, mật khẩu dưới 6 ký tự hoặc xác nhận không khớp: không hoàn tất form.
5. Chọn “Lỗi trùng lặp”: điền sẵn email dùng để thử lỗi; gửi form vẫn bị từ chối.
6. Chọn “Thành công”: điền dữ liệu mẫu; chỉ khi gửi form hợp lệ mới ghi nhận một sự kiện đăng ký. Gửi liên tiếp trong lúc xử lý không nhân đôi sự kiện.
7. Đổi hiện/ẩn mật khẩu, điều hướng bằng bàn phím, xem trên màn hình hẹp.
8. Tắt quyền lưu storage: có thông báo, form vẫn cho thử kết quả.
9. Đăng nhập hoặc chọn “Trải nghiệm khách hàng”: tới trang sản phẩm. Mở trực tiếp sản phẩm khi chưa có phiên: hiển thị “Khách demo”.
10. Tìm “dien thoai”, tên hoặc mã sản phẩm; kết hợp với danh mục; từ khóa không khớp hiển thị trạng thái rỗng. “Xóa bộ lọc” đặt lại cả từ khóa và danh mục.
11. Nhấn ảnh, tên và nút chi tiết ở từng lần mở: mỗi lần tăng đúng một lượt nhấn; bộ lọc và việc đóng hộp thoại không tăng lượt nhấn.
12. Dùng bàn phím mở hộp thoại, Tab qua các nút và Escape để đóng; focus quay về nút vừa mở.
13. Ảnh không tải được: dùng ảnh thay thế cục bộ; tên, giá và thông số vẫn hiển thị.
14. Trên cùng một nguồn localhost và cùng tab, tải lại/đổi trang vẫn giữ phiên và sự kiện sản phẩm; không lưu mật khẩu.

## Phần tiếp theo, chưa triển khai trong commit này

3. Dashboard, biểu đồ, bảng log và liên kết sự kiện demo.

Phần 2 dừng tại trang sản phẩm để nhóm kiểm tra và commit riêng. Phần 3 chỉ được triển khai ở lượt tiếp theo. Chưa khởi chạy ứng dụng, trình duyệt, server, build hoặc test; các mục trên là hướng dẫn kiểm tra thủ công.
