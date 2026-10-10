package com.Logcollector.server.Storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class LogRotation {

    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final Duration RETENTION = Duration.ofDays(15);
    private static final Pattern ARCHIVE_NAME = Pattern.compile(
            "server-[0-9]{8}-[0-9]{6}-[0-9]{3}\\.log");
    private static final DateTimeFormatter ARCHIVE_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final Path directory;
    private final Path activeLog;

    /** Dùng logs/server.log tương đối với thư mục chạy; không tạo thư mục logs. */
    public LogRotation() throws IOException {
        this(Paths.get("logs"));
    }

    /** Nhận thư mục log có sẵn; tên tệp hiện hành luôn là server.log. */
    public LogRotation(Path logDirectory) throws IOException {
        Path supplied = Objects.requireNonNull(logDirectory, "logDirectory")
                .toAbsolutePath().normalize();
        BasicFileAttributes attributes = Files.readAttributes(
                supplied, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Log directory must be an existing real directory: " + supplied);
        }
        directory = supplied.toRealPath();
        activeLog = directory.resolve("server.log");
    }

    public Path getActiveLogPath() {
        return activeLog;
    }

    /**
     * Kiểm tra trước khi ghi, sau khi bộ ghi đã flush dưới khóa chung.
     * Đúng 20 MiB vẫn được ghi; chỉ rotation khi tổng kích thước sẽ vượt ngưỡng.
     * Tệp chưa tồn tại được xem là 0 byte, nhưng lỗi quyền truy cập được báo ra.
     *
     * @throws IllegalArgumentException nếu kích thước âm hoặc bản ghi quá 20 MiB
     * @throws IOException nếu không đọc được metadata hoặc tệp không hợp lệ
     */
    public boolean shouldRotate(long nextRecordBytes) throws IOException {
        validateRecordSize(nextRecordBytes);
        requireDirectory();
        BasicFileAttributes attributes = activeAttributes();
        return nextRecordBytes > 0 && attributes != null
                && attributes.size() > MAX_FILE_BYTES - nextRecordBytes;
    }

    /**
     * Chỉ gọi khi bên gọi giữ khóa chung và đã flush/đóng mọi handle ghi.
     * Kiểm tra lại kích thước sau khi đóng; trả empty nếu không cần rotation.
     * Không tự đóng bộ ghi, tạo server.log mới hay tiếp tục ghi thay bên gọi.
     *
     * <p>Đổi tên trong cùng thư mục, không REPLACE_EXISTING và không ATOMIC_MOVE
     * (ATOMIC_MOVE không bảo đảm giữ tệp đích khi đích đã tồn tại trên mọi provider).
     * Trùng tên gây FileAlreadyExistsException; bên gọi giữ bản ghi và thử lại
     * ở thời điểm khác sau khi kiểm tra trạng thái. Không tạo hậu tố sai quy tắc.
     * Nếu move gặp lỗi, không tự xóa/copy/truncate để phục hồi; bên gọi phải kiểm
     * tra trạng thái trước khi ghi tiếp. Không bảo đảm phục hồi khi mất điện.
     *
     * @return đường dẫn archive nếu đã đổi tên thành công
     * @throws IOException nếu kiểm tra hoặc đổi tên thất bại
     */
    public Optional<Path> rotateClosedLog(long nextRecordBytes) throws IOException {
        if (!shouldRotate(nextRecordBytes)) {
            return Optional.empty();
        }
        // Làm tròn lên tới mili giây để retention không kết thúc sớm vì mất phần nano.
        Instant now = Instant.now();
        Instant rotatedAt = now.truncatedTo(ChronoUnit.MILLIS);
        if (rotatedAt.isBefore(now)) {
            rotatedAt = rotatedAt.plusMillis(1);
        }
        String name = "server-" + ARCHIVE_TIME.format(
                LocalDateTime.ofInstant(rotatedAt, ZoneOffset.UTC)) + ".log";
        if (!ARCHIVE_NAME.matcher(name).matches()) {
            throw new IOException("Rotation time cannot be represented in the archive name");
        }
        Path archive = directory.resolve(name);
        Files.move(activeLog, archive);
        return Optional.of(archive);
    }

    /**
     * Dọn các archive trực tiếp trong thư mục, không duyệt thư mục con.
     * Chỉ xóa tệp thường có tên đúng mẫu và timestamp UTC hợp lệ, khi tuổi
     * vượt 15 x 24 giờ. Tuổi dựa vào thời điểm rotation trong tên, không dùng mtime.
     * Bỏ qua symlink, thư mục, ngày không hợp lệ và hard link trỏ tới server.log.
     * Bên gọi phải giữ cùng khóa với bộ ghi/rotation; archive phải đã đóng.
     *
     * @return các tệp không xử lý được cùng lỗi tương ứng; tiếp tục với tệp khác
     * @throws IOException nếu không thể kiểm tra hoặc duyệt thư mục quản lý
     */
    public Map<Path, IOException> cleanupExpiredLogs() throws IOException {
        requireDirectory();
        Instant cutoff = Instant.now().minus(RETENTION);
        Map<Path, IOException> failures = new LinkedHashMap<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!ARCHIVE_NAME.matcher(name).matches()) {
                    continue;
                }
                Instant rotatedAt;
                try {
                    rotatedAt = LocalDateTime.parse(
                            name.substring("server-".length(), name.length() - ".log".length()),
                            ARCHIVE_TIME).toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException invalidName) {
                    continue;
                }
                if (!rotatedAt.isBefore(cutoff)) {
                    continue;
                }
                try {
                    BasicFileAttributes attributes = Files.readAttributes(
                            entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                        continue;
                    }
                    if (activeAttributes() != null && Files.isSameFile(entry, activeLog)) {
                        continue;
                    }
                    Files.delete(entry);
                } catch (NoSuchFileException disappeared) {
                    // Tệp không còn tồn tại; không cần xóa nữa.
                } catch (IOException failure) {
                    failures.put(entry, failure);
                } catch (SecurityException denied) {
                    failures.put(entry, new IOException("Access denied: " + entry, denied));
                }
            }
        } catch (DirectoryIteratorException failure) {
            throw failure.getCause();
        }
        return Collections.unmodifiableMap(failures);
    }

    /** Chạy thử đơn luồng bằng dữ liệu tạm; không sử dụng log thật của server. */
    public static void main(String[] args) throws IOException {
        Path testDir = Files.createTempDirectory("test-logrotation-").toRealPath();
        System.out.println("===== TEST ISSUE #6 - LOG ROTATION =====");
        System.out.println("Thu muc test: " + testDir);
        try {
            LogRotation rotation = new LogRotation(testDir);
            Path activeLog = rotation.getActiveLogPath();

            // DỮ LIỆU THỬ: sửa tại đây nếu muốn đổi kịch bản.
            byte[] sample = new byte[(int) MAX_FILE_BYTES];
            byte[] nextRecord = "INFO: ban ghi moi\n".getBytes(StandardCharsets.UTF_8);
            int expiredDays = 16;
            int recentDays = 14;
            Arrays.fill(sample, (byte) 'A');
            sample[sample.length - 1] = '\n';
            Files.write(activeLog, sample, StandardOpenOption.CREATE_NEW);

            checkDemo(Files.size(activeLog) == MAX_FILE_BYTES,
                    "Tao log mau dung 20 MiB");
            checkDemo(!rotation.shouldRotate(0), "Khong rotation khi khong ghi them");
            checkDemo(rotation.shouldRotate(nextRecord.length),
                    "Rotation khi ghi them se vuot 20 MiB");

            // Files.write đã đóng handle. Bộ ghi thực tế cần khóa chung và flush/đóng.
            Path archive = rotation.rotateClosedLog(nextRecord.length)
                    .orElseThrow(() -> new IllegalStateException("Rotation khong thanh cong"));
            checkDemo(!Files.exists(activeLog)
                    && Arrays.equals(sample, Files.readAllBytes(archive)),
                    "Archive giu nguyen du lieu: " + archive.getFileName());

            Files.write(activeLog, nextRecord, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            checkDemo(Arrays.equals(nextRecord, Files.readAllBytes(activeLog)),
                    "Mo lai va ghi du ban ghi vao server.log moi");

            Instant now = Instant.now();
            Path oldLog = testDir.resolve("server-" + ARCHIVE_TIME.format(LocalDateTime.ofInstant(
                    now.minus(Duration.ofDays(expiredDays)), ZoneOffset.UTC)) + ".log");
            Path recentLog = testDir.resolve("server-" + ARCHIVE_TIME.format(LocalDateTime.ofInstant(
                    now.minus(Duration.ofDays(recentDays)), ZoneOffset.UTC)) + ".log");
            Files.write(oldLog, nextRecord, StandardOpenOption.CREATE_NEW);
            Files.write(recentLog, nextRecord, StandardOpenOption.CREATE_NEW);

            Map<Path, IOException> failures = rotation.cleanupExpiredLogs();
            checkDemo(failures.isEmpty(), "Cleanup khong co loi: " + failures);
            checkDemo(!Files.exists(oldLog), "Xoa archive " + expiredDays + " ngay tuoi");
            checkDemo(Files.exists(recentLog) && Files.exists(archive),
                    "Giu archive " + recentDays + " ngay tuoi va archive vua tao");
            checkDemo(Arrays.equals(nextRecord, Files.readAllBytes(activeLog)),
                    "Khong xoa hoac thay doi server.log hien hanh");
        } finally {
            // Chỉ dọn các mục trực tiếp trong thư mục tạm của lần chạy này.
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(testDir)) {
                for (Path entry : entries) {
                    Files.deleteIfExists(entry);
                }
            }
            Files.delete(testDir);
            System.out.println("Da don du lieu test tam.");
        }
        System.out.println("ALL 9 CHECKS PASSED");
    }

    private static void checkDemo(boolean success, String description) {
        if (!success) {
            throw new IllegalStateException("[FAIL] " + description);
        }
        System.out.println("[PASS] " + description);
    }

    private static void validateRecordSize(long bytes) {
        if (bytes < 0 || bytes > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Record size must be between 0 and "
                    + MAX_FILE_BYTES + " bytes; retain oversized records in the caller");
        }
    }

    private void requireDirectory() throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()
                || !directory.equals(directory.toRealPath())) {
            throw new IOException("Log directory has changed: " + directory);
        }
    }

    private BasicFileAttributes activeAttributes() throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(
                    activeLog, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException missing) {
            return null;
        }
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IOException("Active log must be a regular file, not a link: " + activeLog);
        }
        return attributes;
    }

    
}
