package com.Logcollector.server.UI.desktop;

import com.Logcollector.server.UI.DashboardUI;

import com.Logcollector.server.Parser.LogEvent;
import com.Logcollector.server.Parser.LogParser;
import javax.swing.SwingUtilities;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Owns source generations, background parsing/filtering, bounded ingress and EDT publication. */
final class DesktopController implements AutoCloseable {
    static final int QUEUE_LIMIT = 20_000;
    record Input(String text, boolean demo, long receivedAt) {}
    record View(DesktopStatistics.Snapshot data, String status, boolean demo, long dropped, long malformed) {}
    static final class Session {
        final ArrayBlockingQueue<Input> queue = new ArrayBlockingQueue<>(QUEUE_LIMIT);
        final AtomicLong dropped = new AtomicLong(), malformed = new AtomicLong();
        final DesktopStatistics stats = new DesktopStatistics(System.currentTimeMillis());
        final boolean demo;
        volatile String status = "Chờ kết nối";
        DesktopSource source;
        Session(boolean demo) { this.demo = demo; }
        boolean offer(Input input) { boolean ok = queue.offer(input); if (!ok) dropped.incrementAndGet(); return ok; }
    }
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(DesktopSource.daemon("ui-statistics"));
    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), DesktopSource.daemon("ui-test-sender"), new ThreadPoolExecutor.AbortPolicy());
    private final Consumer<View> update;
    private final AtomicBoolean publishing = new AtomicBoolean();
    private final AtomicReference<Runnable> latest = new AtomicReference<>();
    private volatile Session session;
    private volatile boolean closed;
    private volatile Socket sendingSocket;
    private volatile DesktopStatistics.Filter groupFilter = DesktopStatistics.Filter.all(), logFilter = DesktopStatistics.Filter.all();
    private volatile String host = "localhost";
    private volatile int port = 9999;

    DesktopController(Consumer<View> update) {
        this.update = update;
        worker.scheduleWithFixedDelay(this::tickSafely, 0, 250, TimeUnit.MILLISECONDS);
    }
    void filters(DesktopStatistics.Filter groups, DesktopStatistics.Filter logs) { groupFilter = groups; logFilter = logs; }
    void configureTcp(String host, int port) {
        if (host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("Địa chỉ/cổng TCP không hợp lệ");
        this.host = host.trim(); this.port = port;
    }
    void select(boolean demo, String address) {
        URI uri = demo ? null : URI.create(address.trim());
        if (!demo && (!("ws".equals(uri.getScheme()) || "wss".equals(uri.getScheme())) || uri.getHost() == null))
            throw new IllegalArgumentException("Nhập địa chỉ ws://host:port hoặc wss://host:port");
        Session previous = session;
        Session next = new Session(demo);
        session = next;
        if (previous != null) previous.source.close();
        java.util.function.BiConsumer<Boolean, String> status = (connected, text) -> {
            if (session != next || closed) return;
            next.stats.connection(connected, System.currentTimeMillis()); next.status = text;
        };
        next.source = demo ? new DesktopSource.Demo(status) : new DesktopSource.Server(uri,
                text -> { if (session == next && !closed) next.offer(new Input(text, false, System.currentTimeMillis())); },
                status, next.malformed::incrementAndGet);
        next.source.start();
    }
    private void tickSafely() {
        if (closed) return;
        try {
            Session current = session;
            if (current == null) return;
            Input input;
            int remaining = QUEUE_LIMIT;
            while (remaining-- > 0 && (input = current.queue.poll()) != null) {
                try { consume(current, input); } catch (RuntimeException error) { current.malformed.incrementAndGet(); }
            }
            try {
                for (String b : com.Logcollector.server.ServerMain.BLOCKED_IPS) {
                    if (!current.stats.isBlocked(b)) {
                        current.stats.blockIp(b, "Đang bị khóa trong Tường lửa Server", "");
                    }
                }
            } catch (Throwable ignored) {}
            var snapshot = current.stats.snapshot(System.currentTimeMillis(), groupFilter, logFilter);
            View view = new View(snapshot, current.status, current.demo, current.dropped.get(), current.malformed.get());
            latest.set(() -> { if (!closed && session == current) update.accept(view); });
            publish();
        } catch (RuntimeException error) { System.err.println("[Desktop UI] " + error.getMessage()); }
    }
    private void publish() {
        if (!publishing.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
            try { Runnable value = latest.getAndSet(null); if (value != null) value.run(); }
            finally { publishing.set(false); if (latest.get() != null && !closed) publish(); }
        });
    }
    static void consume(Session target, Input input) {
        if (input.demo) {
            LogEvent log = LogParser.parse(input.text);
            if (log != null) target.stats.add(DesktopLog.of(log, input.receivedAt, "DEMO", input.text));
            return;
        }
        Map<String, Object> data = DesktopJson.object(input.text);
        String type = DesktopJson.string(data, "type");
        if (type.equals("LOG")) {
            if (!data.containsKey("message") || !data.containsKey("level")) throw new IllegalArgumentException("Incomplete LOG");
            LogEvent log = new LogEvent(DesktopJson.string(data, "ip"), DesktopJson.string(data, "device"),
                    DesktopJson.string(data, "category"), DesktopJson.string(data, "timestamp"), 0,
                    DesktopJson.string(data, "level"), DesktopJson.string(data, "message"));
            // Facility is not a field in the broadcast; recover it only from the actual raw log.
            String raw = DesktopJson.string(data, "raw");
            if (!raw.isBlank()) { LogEvent parsed = LogParser.parse(raw); if (parsed != null) log.setFacility(parsed.getFacility()); }
            target.stats.add(DesktopLog.of(log, input.receivedAt, "SERVER", raw));
        } else if (Set.of("ALERT", "IP_BLOCKED", "IP_UNBLOCKED", "ALL_IPS_UNBLOCKED").contains(type)) {
            String name = type.equals("ALERT") ? DesktopJson.string(data, "alertType") : type;
            String message = DesktopJson.string(data, "message");
            if (message.isEmpty()) message = DesktopJson.string(data, "reason");
            String ip = DesktopJson.string(data, "ip");
            String timestamp = DesktopJson.string(data, "timestamp");
            if ("IP_BLOCKED".equals(type)) {
                target.stats.blockIp(ip, message, timestamp);
            } else if ("ALL_IPS_UNBLOCKED".equals(type)) {
                target.stats.unblockAll();
                target.stats.clearBlockedAlerts();
                if (ip.isEmpty()) ip = "TẤT CẢ IP";
                if (message.isEmpty()) message = "Đã mở khóa toàn bộ IP khỏi danh sách đen Tường lửa!";
            } else if ("IP_UNBLOCKED".equals(type)) {
                target.stats.unblockIp(ip);
                target.stats.removeBlockedAlertForIp(ip);
                if (message.isEmpty()) message = "Đã mở khóa IP " + ip;
            }
            target.stats.alert(new DesktopStatistics.Alert(timestamp, name, ip, message));
        }
    }
    void details(DesktopStatistics.Snapshot snapshot, String ip, String group, Consumer<List<DesktopLog>> callback) {
        // The panel captures the complete retained snapshot, so details cannot drift while logs arrive.
        worker.execute(() -> {
            List<DesktopLog> result = snapshot.allLogs().stream()
                    .filter(l -> ip == null || ip.equals(l.event().getIp()))
                    .filter(l -> group == null || group.equals(l.groupKey())).toList();
            SwingUtilities.invokeLater(() -> { if (!closed) callback.accept(result); });
        });
    }
    static String testMessage(String action, String description, boolean warning) {
        String safe = description.replace('\r', ' ').replace('\n', ' ');
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return "<" + (warning ? 12 : 14) + ">" + time + " " + DashboardUI.getLocalLanIp()
                + " " + action + " - Device 'Java Desktop Test' [UI_TEST] " + safe;
    }
    void emit(String action, String description, boolean warning, Consumer<String> feedback) {
        Session target = session;
        if (target == null || closed) return;
        String targetHost = host; int targetPort = port;
        try {
            sender.execute(() -> {
                if (session != target || closed) return;
                String text = testMessage(action, description, warning);
                String response;
                if (target.demo) {
                    boolean ok = target.offer(new Input(text, true, System.currentTimeMillis()));
                    response = ok ? "Đã tạo 1 log DEMO: " + action : "Hàng đợi đầy — log demo chưa được ghi nhận";
                } else {
                    try (Socket socket = new Socket()) {
                        sendingSocket = socket;
                        socket.connect(new InetSocketAddress(targetHost, targetPort), 3000);
                        socket.getOutputStream().write((text + "\n").getBytes(StandardCharsets.UTF_8));
                        socket.getOutputStream().flush();
                        response = "Đã gửi TCP: " + action + " — kiểm tra Server phát lại ở Nhật ký trực tiếp";
                    } catch (IOException error) { response = "Gửi TCP thất bại: " + error.getMessage(); }
                    finally { sendingSocket = null; }
                }
                String result = response;
                SwingUtilities.invokeLater(() -> { if (!closed && session == target) feedback.accept(result); });
            });
        } catch (RejectedExecutionException error) { feedback.accept("Hàng đợi gửi đang đầy; thao tác chưa gửi log."); }
    }
    boolean sendCommand(String jsonCommand) {
        Session current = session;
        if (current != null && current.source != null) {
            return current.source.send(jsonCommand);
        }
        return false;
    }

    boolean unblockIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String cleanIp = ip.trim();
        boolean localRemoved = false;
        try {
            localRemoved = com.Logcollector.server.ServerMain.unblockIp(cleanIp);
        } catch (Throwable ignored) {}

        String command = String.format("{\"action\":\"UNBLOCK_IP\",\"ip\":\"%s\"}", cleanIp);
        boolean sent = sendCommand(command);

        Session current = session;
        if (current != null) {
            current.stats.unblockIp(cleanIp);
            current.stats.removeBlockedAlertForIp(cleanIp);
        }

        return localRemoved || sent;
    }

    boolean unblockAllIps() {
        boolean localCalled = false;
        try {
            com.Logcollector.server.ServerMain.unblockAllIps();
            localCalled = true;
        } catch (Throwable ignored) {}

        String command = "{\"action\":\"UNBLOCK_ALL\"}";
        boolean sent = sendCommand(command);

        Session current = session;
        if (current != null) {
            current.stats.unblockAll();
            current.stats.clearBlockedAlerts();
        }

        return localCalled || sent;
    }

    boolean isIpBlocked(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String cleanIp = ip.trim();
        Session current = session;
        if (current != null && current.stats.isBlocked(cleanIp)) {
            return true;
        }
        try {
            return com.Logcollector.server.ServerMain.BLOCKED_IPS.contains(cleanIp);
        } catch (Throwable t) {
            return false;
        }
    }

    public void close() {
        closed = true;
        Session target = session;
        if (target != null && target.source != null) target.source.close();
        Socket socket = sendingSocket;
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
        worker.shutdownNow(); sender.shutdownNow(); latest.set(null);
    }
}
