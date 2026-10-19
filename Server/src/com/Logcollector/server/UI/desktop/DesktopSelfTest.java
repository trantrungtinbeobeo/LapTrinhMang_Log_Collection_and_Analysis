package com.Logcollector.server.UI.desktop;

import com.Logcollector.server.UI.DashboardUI;

import com.Logcollector.server.Parser.LogEvent;
import com.Logcollector.server.Parser.LogParser;
import com.Logcollector.server.RealTime.WebSocketServer;
import com.Logcollector.server.Core.TCPReceiver;
import com.Logcollector.server.Core.LogQueue;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Regression suite without a test framework. Visual checks use the same FlatLaf runtime as the app. */
public final class DesktopSelfTest {
    private static int checks;
    private static final DesktopStatistics.Filter ALL = DesktopStatistics.Filter.all();
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static DesktopLog log(long time, String ip, String level, String message) {
        return DesktopLog.of(new LogEvent(ip, "2026-10-18 12:00:" + time % 60, 1, level, message), time, "TEST", message);
    }
    static DesktopStatistics.Snapshot snap(DesktopStatistics s, long now) { return s.snapshot(now, ALL, ALL); }
    static void statistics() {
        DesktopStatistics s = new DesktopStatistics(1_000_000);
        var zero = snap(s, 1_000_000);
        check(zero.total() == 0 && zero.axisMax() == 100 && zero.groups().isEmpty(), "empty state");
        s.connection(true, 1_000_000);
        s.add(log(1_000_050, "10.0.0.1", "WARN", "LOGIN_FAILED user alpha"));
        var one = snap(s, 1_001_000);
        check(one.total() == 1 && one.current() == 1 && one.severity().get("WARNING") == 1, "single log and WARN");
        s.add(log(1_001_050, "10.0.0.2", "WARNING", "LOGIN_FAILED user beta"));
        s.add(log(1_001_060, "10.0.0.1", "INFO", "VIEW_PRODUCT one"));
        s.add(log(1_001_070, "10.0.0.1", "ERROR", "Disk 42 failed"));
        s.add(log(1_001_080, "10.0.0.1", "ERROR", "Disk 43 failed"));
        var data = snap(s, 1_002_000);
        check(data.groups().get(0).count() == 2 && data.groups().size() == 4, "event grouping ignores timestamp, preserves meaningful numbers");
        check(data.ips().get(0).address().equals("10.0.0.1") && data.ips().get(0).count() == 4, "IP ranking");
        // ── Giai đoạn 3: Ip severity breakdown và latestTime ──────────────────
        var top = data.ips().get(0); // 10.0.0.1: WARNING×1, INFO×1, ERROR×2 (4 total)
        check(top.severity() != null && !top.severity().isEmpty(), "Ip has severity map");
        long sevSum = top.severity().values().stream().mapToLong(Long::longValue).sum();
        check(sevSum == top.count(), "severity sum matches IP total count");
        check(top.severity().getOrDefault("ERROR", 0L) == 2 && top.severity().getOrDefault("INFO", 0L) == 1
                && top.severity().getOrDefault("WARNING", 0L) == 1, "severity breakdown per-IP correct");
        check(!top.latestTime().isBlank(), "Ip latestTime is populated");
        var second = data.ips().get(1); // 10.0.0.2: WARNING×1
        check(second.severity().getOrDefault("WARNING", 0L) == 1 && second.severity().size() == 1, "second IP severity isolated from first");
        // ──────────────────────────────────────────────────────────────────────
        var filtered = s.snapshot(1_002_000, new DesktopStatistics.Filter("WARNING", "beta", true), new DesktopStatistics.Filter("ERROR", "42", false));
        check(filtered.groups().size() == 1 && filtered.groups().get(0).count() == 2 && filtered.logs().size() == 1, "combined filters and whole matching group");
        check(filtered.total() == 5 && filtered.allLogs().size() == 5, "filters never affect KPI or frozen details");
        var asc = s.snapshot(1_002_000, new DesktopStatistics.Filter("Tất cả", "", true), ALL);
        check(asc.groups().get(0).count() == 1 && asc.groups().get(asc.groups().size() - 1).count() == 2, "ascending numeric ranking");
        s.connection(false, 1_002_100); s.connection(true, 1_004_100);
        var gap = snap(s, 1_006_000);
        check(gap.everDisconnected() && gap.rates().stream().filter(r -> !r.available()).count() == 3, "disconnect and partial reconnect seconds are gaps");
        check(Math.abs(gap.average() - 5.0 / 3) < 0.001 && gap.current() == 0, "exclude gaps, include connected zero seconds");
        for (int peak : new int[]{5, 150, 1500, 10000}) {
            DesktopStatistics rate = new DesktopStatistics(0); rate.connection(true, 0);
            for (int i = 0; i < peak; i++) rate.add(log(10, "127.0.0.1", "INFO", "TRAFFIC_TEST"));
            var sample = snap(rate, 1000);
            check(sample.current() == peak && sample.peak() == peak && sample.axisMax() >= peak + 100, "dynamic axis " + peak);
            check(snap(rate, 301000).peak() == 0 && snap(rate, 301000).axisMax() == 100, "expire peak without new logs");
            check(snap(rate, 301000).rates().size() == 300, "bounded chart window");
        }
        DesktopStatistics bounded = new DesktopStatistics(0); bounded.connection(true, 0);
        bounded.add(log(1, "old", "INFO", "OLD_EVENT"));
        for (int i = 0; i < 10000; i++) bounded.add(log(2, "127.0.0.1", "DEBUG", "NEW_EVENT"));
        for (int i = 0; i < 1005; i++) bounded.alert(new DesktopStatistics.Alert("t", "ALERT", "ip", "" + i));
        var limited = snap(bounded, 1000);
        check(limited.total() == 10001 && limited.retained() == 10000 && limited.ipCount() == 1 && limited.groups().size() == 1, "bounded records, exact retained ranking, session counters");
        check(limited.severity().get("INFO") == 1 && limited.severity().get("DEBUG") == 10000, "severity survives detail eviction");
        check(limited.alerts().size() == 1000 && limited.alerts().get(0).message().equals("1004"), "bounded alerts");
        // ── Giai đoạn 3: rank stability after eviction ────────────────────────
        var evictedTop = limited.ips().get(0); // 127.0.0.1 DEBUG×10000 (old "old" IP evicted from retained)
        check(evictedTop.severity().getOrDefault("DEBUG", 0L) == limited.retained(), "severity matches retained after eviction");
        // ──────────────────────────────────────────────────────────────────────
        DesktopController.Session queue = new DesktopController.Session(true);
        for (int i = 0; i < DesktopController.QUEUE_LIMIT + 3; i++) queue.offer(new DesktopController.Input("x", true, 0));
        check(queue.queue.size() == 20000 && queue.dropped.get() == 3, "bounded input with explicit drops");
    }
    static void parsingAndFixtures() {
        var json = DesktopJson.object("{\"message\":\"Tiếng Việt \\n \\\"quote\\\" \\u0041\",\"n\":12,\"nested\":[true,null,{\"x\":false}]}");
        check(DesktopJson.string(json, "message").equals("Tiếng Việt \n \"quote\" A"), "JSON escapes and unicode");
        for (String invalid : List.of("{", "{\"a\":1,}", "{} garbage", "{\"a\":01}", "[]")) {
            boolean rejected = false; try { DesktopJson.object(invalid); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "reject malformed JSON " + invalid);
        }
        DesktopController.Session session = new DesktopController.Session(false);
        DesktopController.consume(session, new DesktopController.Input("{\"type\":\"LOG\",\"ip\":\"1.2.3.4\",\"level\":\"CRITICAL\",\"timestamp\":\"t\",\"message\":\"PORT_SCAN test\",\"raw\":\"<10>raw\"}", false, System.currentTimeMillis()));
        DesktopController.consume(session, new DesktopController.Input("{\"type\":\"ALERT\",\"alertType\":\"BRUTE_FORCE\",\"message\":\"test\"}", false, System.currentTimeMillis()));
        DesktopController.consume(session, new DesktopController.Input("{\"type\":\"IP_BLOCKED\",\"ip\":\"1.2.3.4\",\"reason\":\"test\"}", false, System.currentTimeMillis()));
        var parsed = snap(session.stats, System.currentTimeMillis());
        check(parsed.total() == 1 && parsed.alerts().size() == 2 && parsed.severity().get("CRITICAL") == 1, "existing wire messages");
        check(DesktopCatalog.PRODUCTS.size() == 8 && DesktopCatalog.PRODUCTS.get(0).price() == 49990000L, "catalog preserves fixtures");
        DesktopTestTools.Cart cart = new DesktopTestTools.Cart(); var p = DesktopCatalog.PRODUCTS.get(0);
        cart.add(p); cart.add(p); check(cart.count() == 2 && cart.total() == p.price() * 2, "cart totals");
        cart.quantity(p, 3); check(cart.count() == 3, "cart quantity"); cart.quantity(p, 0); check(cart.empty(), "remove cart item");
        cart.add(p); cart.clear(); check(cart.total() == 0, "clear and checkout cart");
        check(DesktopTestTools.accepts("minhan@demostore.vn", "Demo123!".toCharArray()), "demo password");
        check(DesktopTestTools.accepts("test@example.com", "admin123".toCharArray()), "legacy quick-test password");
        check(!DesktopTestTools.accepts("bad", "Demo123!".toCharArray()) && !DesktopTestTools.accepts("a@b.vn", "wrong".toCharArray()), "failed login");
        String message = DesktopController.testMessage("LOGIN_FAILED", "failed password user 'demo'\ninjection", true);
        check(!message.contains("\n") && LogParser.parse(message).getSeverityLevel().equals("WARNING") && message.contains("[UI_TEST]"), "test syslog matches parser and cannot inject a second record");
    }
    static void load() {
        DesktopStatistics state = new DesktopStatistics(0); state.connection(true, 0);
        long start = System.nanoTime();
        for (int second = 0; second < 10; second++) {
            for (int i = 0; i < 10000; i++) state.add(log(second * 1000L + 10, "10.0.0." + i % 32, "INFO", "LOAD_TEST item=" + i));
            var snapshot = snap(state, (second + 1) * 1000L);
            check(snapshot.current() == 10000 && snapshot.retained() == 10000 && snapshot.axisMax() >= 10100, "10k/sec sample " + second);
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        check(snap(state, 10000).total() == 100000, "load exact total");
        System.out.println("[LOAD] 100,000 synthetic logs + 10 snapshots in " + millis + " ms; this measures UI processing, not Server throughput.");
    }
    static int freePort() throws IOException { try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); } }
    static void await(BooleanSupplier done, String name) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (!done.getAsBoolean() && System.nanoTime() < until) Thread.sleep(30);
        check(done.getAsBoolean(), name);
    }
    static void integration() throws Exception {
        int wsPort = freePort(), tcpPort = freePort();
        WebSocketServer server = new WebSocketServer(wsPort);
        TCPReceiver receiver = new TCPReceiver(tcpPort);
        Thread tcp = DesktopSource.daemon("test-tcp").newThread(receiver);
        AtomicReference<DesktopController.View> view = new AtomicReference<>();
        DesktopController controller = new DesktopController(view::set);
        WebSocketServer restarted = null;
        try {
            server.start(); tcp.start();
            controller.configureTcp("localhost", tcpPort);
            controller.select(false, "ws://localhost:" + wsPort);
            await(() -> view.get() != null && view.get().status().startsWith("Đã kết nối"), "actual existing WebSocket handshake");
            AtomicReference<String> feedback = new AtomicReference<>();
            controller.emit("VIEW_PRODUCT", "test product", false, feedback::set);
            await(() -> feedback.get() != null, "TCP send callback");
            check(feedback.get().startsWith("Đã gửi TCP"), "actual TCPReceiver accepted send");
            check(view.get().data().total() == 0, "TCP send alone never increases live KPI");
            ExecutorService take = Executors.newSingleThreadExecutor(DesktopSource.daemon("test-queue"));
            String received;
            try { received = take.submit(LogQueue::take).get(4, TimeUnit.SECONDS); } finally { take.shutdownNow(); }
            check(received.contains("VIEW_PRODUCT") && received.contains("[UI_TEST]"), "existing receiver delivered one syslog to queue");
            server.broadcast("{\"type\":\"LOG\",\"timestamp\":\"2026-10-18 12:00:00\",\"ip\":\"127.0.0.1\",\"level\":\"INFO\",\"message\":\"VIEW_PRODUCT test Tiếng Việt\"}");
            server.broadcast("{\"type\":\"ALERT\",\"alertType\":\"BRUTE_FORCE\",\"ip\":\"127.0.0.1\",\"message\":\"integration fixture\"}");
            await(() -> view.get().data().total() == 1 && view.get().data().alerts().size() == 1, "live log plus alert parsed");
            long liveSessionStart = view.get().data().startedAt();
            SwingUtilities.invokeAndWait(() -> { DesktopTheme.install(); DesktopTheme.setDark(true); DesktopTheme.setDark(false); });
            check(view.get().data().startedAt() == liveSessionStart && view.get().data().total() == 1
                    && view.get().status().startsWith("Đã kết nối"), "theme switch preserves live connection and counters");
            server.broadcast("malformed");
            await(() -> view.get().malformed() == 1, "invalid message reported without killing connection");
            server.stop();
            await(() -> view.get().data().everDisconnected(), "disconnect recorded");
            restarted = new WebSocketServer(wsPort); restarted.start();
            await(() -> view.get().status().startsWith("Đã kết nối"), "automatic reconnect");
            restarted.broadcast("{\"type\":\"LOG\",\"ip\":\"127.0.0.1\",\"level\":\"INFO\",\"message\":\"VIEW_PRODUCT after reconnect\"}");
            await(() -> view.get().data().total() == 2, "reconnect preserves totals without duplicated listeners");
            controller.select(true, "");
            await(() -> view.get().demo() && view.get().data().total() == 0, "source switch resets session");
            controller.emit("LOGIN_FAILED", "failed password", true, feedback::set);
            await(() -> view.get().data().total() == 1 && view.get().data().severity().getOrDefault("WARNING", 0L) == 1, "local demo pipeline");
            controller.select(false, "ws://localhost:" + wsPort);
            await(() -> !view.get().demo() && view.get().status().startsWith("Đã kết nối"), "back to real source");
            controller.configureTcp("localhost", freePort()); feedback.set(null);
            controller.emit("CHECKOUT_ORDER", "test", false, feedback::set);
            await(() -> feedback.get() != null, "failed send callback");
            check(feedback.get().startsWith("Gửi TCP thất bại") && view.get().data().total() == 0, "failed send is not success or demo fallback");
        } finally { controller.close(); receiver.stop(); server.stop(); if (restarted != null) restarted.stop(); }
    }
    static <T extends Component> List<T> descendants(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container container) result.addAll(descendants(container, type));
        }
        return result;
    }
    static JButton button(Container root, String label) { return descendants(root, JButton.class).stream().filter(b -> label.equals(b.getText())).findFirst().orElseThrow(); }
    static void capture(Window frame, Path file) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.validate();
            Container canvas = ((RootPaneContainer) frame).getContentPane();
            var transform = frame.getGraphicsConfiguration().getDefaultTransform();
            BufferedImage image = new BufferedImage((int) Math.ceil(canvas.getWidth() * transform.getScaleX()),
                    (int) Math.ceil(canvas.getHeight() * transform.getScaleY()), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); g.transform(transform); canvas.paintAll(g); g.dispose();
            try { ImageIO.write(image, "png", file.toFile()); } catch (IOException e) { throw new UncheckedIOException(e); }
        });
    }
    static void visual(Path directory) throws Exception {
        Files.createDirectories(directory);
        AtomicReference<JFrame> frameRef = new AtomicReference<>(); AtomicReference<DesktopFrame> panelRef = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                DesktopTheme.install(); JFrame frame = new JFrame("Desktop UI verification"); DesktopFrame panel = new DesktopFrame();
                frameRef.set(frame); panelRef.set(panel); frame.setContentPane(panel); frame.setSize(1360, 1040); frame.setLocationRelativeTo(null); frame.setVisible(true); panel.connect(true);
            });
            DesktopFrame panel = panelRef.get(); JFrame frame = frameRef.get();
            for (int i = 0; i < 30; i++) panel.controller.emit(i % 5 == 0 ? "LOGIN_FAILED" : "VIEW_PRODUCT", "visual fixture " + i, i % 5 == 0, ignored -> {});
            await(() -> panel.monitor.currentSnapshot() != null && panel.monitor.currentSnapshot().total() == 30, "demo actions counted exactly once");
            Thread.sleep(1100); capture(frame, directory.resolve("dashboard.png"));
            themeChecks(panel, frame, directory);
            navigationChecks(panel, frame, directory);
            SwingUtilities.invokeAndWait(() -> { frame.setSize(1024, 760); });
            Thread.sleep(150);
            capture(frame, directory.resolve("dashboard-small.png"));
            SwingUtilities.invokeAndWait(() -> check(!descendants(panel.monitor, JScrollPane.class).get(0).getHorizontalScrollBar().isVisible(), "narrow layout needs no horizontal scroll"));
            SwingUtilities.invokeAndWait(() -> {
                frame.setSize(1360, 960); panel.showPage(DesktopFrame.Page.PRODUCTS);
                Container store = (Container) panel.screen(DesktopFrame.Page.PRODUCTS);
                JTable products = descendants(store, JTable.class).get(0); products.setRowSelectionInterval(0, 0);
                button(store, "Thêm vào giỏ").doClick();
            });
            await(() -> panel.monitor.currentSnapshot().total() == 31, "one product addition creates one log");
            SwingUtilities.invokeAndWait(() -> {
                navigate(panel, DesktopFrame.Page.OVERVIEW); navigate(panel, DesktopFrame.Page.PRODUCTS); frame.validate();
                Container store = panel.screen(DesktopFrame.Page.PRODUCTS);
                check(descendants(store, JTable.class).get(1).getRowCount() == 1, "cart survives navigation");
                JButton checkout = button(store, "Thanh toán giỏ (demo)");
                check(checkout.getVisibleRect().height == checkout.getHeight(), "wide product layout shows checkout without scrolling");
            });
            capture(frame, directory.resolve("products.png"));
            SwingUtilities.invokeAndWait(() -> {
                Container store = (Container) panel.screen(DesktopFrame.Page.PRODUCTS);
                JTable products = descendants(store, JTable.class).get(0);
                JTextField search = descendants(store, JTextField.class).get(0);
                search.setText("PRD-LAP"); products.setRowSelectionInterval(0, 0);
                DesktopTheme.setDark(true);
                check(products.getSelectedRow() == 0 && search.getText().equals("PRD-LAP") && products.getRowCount() == 2,
                        "theme preserves selected row and product filter");
                check(panel.selectedPage() == DesktopFrame.Page.PRODUCTS, "theme preserves selected tab");
            });
            capture(frame, directory.resolve("products-dark.png"));
            SwingUtilities.invokeAndWait(() -> DesktopTheme.setDark(false));
            SwingUtilities.invokeAndWait(() -> {
                Container store = (Container) panel.screen(DesktopFrame.Page.PRODUCTS);
                JTextField search = descendants(store, JTextField.class).get(0); search.setText("PRD-PHN");
                JTable catalog = descendants(store, JTable.class).get(0);
                check(catalog.getRowCount() == 2, "product text filter"); search.setText("");
                button(store, "Thanh toán giỏ (demo)").doClick();
            });
            await(() -> panel.monitor.currentSnapshot().total() == 32, "one checkout, filters never generate logs");
            SwingUtilities.invokeAndWait(() -> {
                panel.showPage(DesktopFrame.Page.LOGIN); Container login = (Container) panel.screen(DesktopFrame.Page.LOGIN);
                descendants(login, JPasswordField.class).get(0).setText("Demo123!"); button(login, "Đăng nhập demo").doClick();
            });
            await(() -> panel.monitor.currentSnapshot().total() == 33, "one successful login event");
            SwingUtilities.invokeAndWait(() -> {
                navigate(panel, DesktopFrame.Page.LOGS); navigate(panel, DesktopFrame.Page.LOGIN);
                check(descendants(panel.screen(DesktopFrame.Page.LOGIN), JLabel.class).stream().anyMatch(label -> label.getText().equals("Phiên demo: minhan@demostore.vn")), "demo identity survives navigation");
            });
            capture(frame, directory.resolve("login.png"));
            SwingUtilities.invokeAndWait(() -> DesktopTheme.setDark(true));
            capture(frame, directory.resolve("login-dark.png"));
            SwingUtilities.invokeAndWait(() -> DesktopTheme.setDark(false));
            SwingUtilities.invokeAndWait(() -> {
                Container login = (Container) panel.screen(DesktopFrame.Page.LOGIN);
                descendants(login, JPasswordField.class).get(0).setText("wrong"); button(login, "Đăng nhập demo").doClick();
            });
            await(() -> panel.monitor.currentSnapshot().total() == 34, "one failed login event");
            check(panel.monitor.currentSnapshot().severity().get("WARNING") == 7, "failed login severity and fixture total");
            check(panel.monitor.currentSnapshot().allLogs().stream().noneMatch(l -> l.raw().contains("Demo123!") || l.raw().contains("wrong")), "password never enters telemetry");
            check(frame.isShowing(), "visible Swing frame, resizing and real fixture buttons");
            connectionChecks(panel, frame, directory);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (panelRef.get() != null) panelRef.get().controller.close();
                if (frameRef.get() != null) { for (Window child : frameRef.get().getOwnedWindows()) child.dispose(); frameRef.get().dispose(); }
            });
        }
        DashboardUI compatibility = new DashboardUI(8080); compatibility.start();
        SwingUtilities.invokeAndWait(() -> {}); compatibility.stop(); SwingUtilities.invokeAndWait(() -> {});
        check(true, "legacy launcher start/stop returns without System.exit");
    }
    static <T extends Component> T named(Container root, Class<T> type, String name) {
        return descendants(root, type).stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }
    static void navigate(DesktopFrame panel, DesktopFrame.Page page) {
        named(panel, JToggleButton.class, "nav-" + page.id).doClick();
    }
    static void navigationChecks(DesktopFrame panel, JFrame frame, Path directory) throws Exception {
        long start = panel.monitor.currentSnapshot().startedAt();
        SwingUtilities.invokeAndWait(() -> {
            navigate(panel, DesktopFrame.Page.LOGS);
            check(panel.screen(DesktopFrame.Page.LOGS).isShowing() && !panel.monitor.isShowing(), "sidebar opens a dedicated live log screen");
            JTextField query = named(panel, JTextField.class, "log-query"); query.setText("visual fixture 1");
            descendants(panel.screen(DesktopFrame.Page.LOGS), JComboBox.class).get(0).setSelectedItem("WARNING");
        });
        await(() -> panel.monitor.currentSnapshot().logs().size() == 2, "live screen combines severity and text filters");
        SwingUtilities.invokeAndWait(() -> descendants(panel.screen(DesktopFrame.Page.LOGS), JTable.class).get(0).setRowSelectionInterval(0, 0));
        capture(frame, directory.resolve("live-logs.png"));
        SwingUtilities.invokeAndWait(() -> {
            navigate(panel, DesktopFrame.Page.EVENTS);
            named(panel, JTextField.class, "event-query").setText("LOGIN_FAILED");
            descendants(panel.screen(DesktopFrame.Page.EVENTS), JComboBox.class).get(1).setSelectedIndex(1);
        });
        await(() -> panel.monitor.currentSnapshot().groups().size() == 1, "dedicated event screen filter");
        capture(frame, directory.resolve("analysis.png"));
        SwingUtilities.invokeAndWait(() -> {
            JTable events = descendants(panel.screen(DesktopFrame.Page.EVENTS), JTable.class).get(0);
            events.setRowSelectionInterval(0, 0); events.getActionMap().get("details").actionPerformed(null);
        });
        await(() -> Arrays.stream(frame.getOwnedWindows()).anyMatch(w -> w instanceof JDialog d && d.isShowing() && d.getTitle().startsWith("Chi tiết •")), "event screen opens grouped details");
        SwingUtilities.invokeAndWait(() -> {
            JDialog detail = (JDialog) Arrays.stream(frame.getOwnedWindows()).filter(w -> w instanceof JDialog d && d.isShowing() && d.getTitle().startsWith("Chi tiết •")).findFirst().orElseThrow();
            JTable table = descendants(detail, JTable.class).get(0);
            check(table.getRowCount() == 6 && table.getValueAt(0, 2).equals("WARNING"), "sorted and filtered group opens the correct records"); detail.dispose();
            for (DesktopFrame.Page page : DesktopFrame.Page.values()) {
                navigate(panel, page); check(panel.screen(page).isShowing() && panel.selectedPage() == page, "sidebar navigation " + page);
            }
            navigate(panel, DesktopFrame.Page.LOGS);
            check(named(panel, JTextField.class, "log-query").getText().equals("visual fixture 1")
                    && descendants(panel.screen(DesktopFrame.Page.LOGS), JTable.class).get(0).getSelectedRow() == 0, "navigation preserves live filter and selected record");
            check(named(panel, JTextField.class, "event-query").getText().equals("LOGIN_FAILED"), "event filter survives navigation independently");
        });
        check(panel.monitor.currentSnapshot().total() == 30 && panel.monitor.currentSnapshot().startedAt() == start, "navigation never resets or generates telemetry");
        SwingUtilities.invokeAndWait(() -> {
            named(panel, JTextField.class, "log-query").setText(""); named(panel, JTextField.class, "event-query").setText("");
            descendants(panel.screen(DesktopFrame.Page.LOGS), JComboBox.class).get(0).setSelectedIndex(0);
            frame.setSize(1000, 680); navigate(panel, DesktopFrame.Page.EVENTS);
        });
        Thread.sleep(150); capture(frame, directory.resolve("events-small.png"));
        SwingUtilities.invokeAndWait(() -> {
            for (DesktopFrame.Page page : DesktopFrame.Page.values()) {
                navigate(panel, page); frame.validate();
                check(named(panel, JToggleButton.class, "nav-" + page.id).isShowing(), "navigation visible at minimum size " + page);
                for (JButton action : descendants(panel.screen(page), JButton.class)) if (action.isShowing() && action.getText() != null && !action.getText().isEmpty())
                    check(action.getWidth() >= action.getPreferredSize().width, "button label fits at minimum width: " + action.getText());
            }
            navigate(panel, DesktopFrame.Page.PRODUCTS);
        });
        capture(frame, directory.resolve("products-small.png"));
        SwingUtilities.invokeAndWait(() -> navigate(panel, DesktopFrame.Page.LOGIN)); capture(frame, directory.resolve("login-small.png"));
        SwingUtilities.invokeAndWait(() -> navigate(panel, DesktopFrame.Page.ALERTS)); capture(frame, directory.resolve("alerts.png"));
        SwingUtilities.invokeAndWait(() -> { navigate(panel, DesktopFrame.Page.OVERVIEW); frame.setSize(1360, 1040); });
    }
    static void connectionChecks(DesktopFrame panel, JFrame frame, Path directory) throws Exception {
        var before = panel.configuration(); long start = panel.monitor.currentSnapshot().startedAt();
        AtomicReference<DesktopConnectionDialog> dialogRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            button(panel, "Kết nối").doClick();
            DesktopConnectionDialog dialog = (DesktopConnectionDialog) Arrays.stream(frame.getOwnedWindows()).filter(w -> w instanceof DesktopConnectionDialog && w.isShowing()).findFirst().orElseThrow();
            dialogRef.set(dialog); named(dialog, JTextField.class, "connection-tcp-host").setText("discarded.example");
            button(dialog, "Hủy").doClick(); check(panel.configuration().equals(before), "cancel discards connection drafts");
            panel.openConnection();
            dialog = (DesktopConnectionDialog) Arrays.stream(frame.getOwnedWindows()).filter(w -> w instanceof DesktopConnectionDialog && w.isShowing()).findFirst().orElseThrow();
            dialogRef.set(dialog);
            named(dialog, JComboBox.class, "connection-source").setSelectedIndex(0);
            named(dialog, JTextField.class, "connection-address").setText("http://invalid");
            button(dialog, "Áp dụng / phiên mới").doClick();
            check(dialog.isShowing() && !named(dialog, JLabel.class, "connection-error").getText().isBlank() && panel.configuration().equals(before), "invalid URL rejected without changing session or configuration");
            named(dialog, JTextField.class, "connection-address").setText(before.address());
            JSpinner port = named(dialog, JSpinner.class, "connection-tcp-port");
            ((JSpinner.DefaultEditor) port.getEditor()).getTextField().setText("not-a-port");
            button(dialog, "Lưu đích TCP").doClick();
            check(dialog.isShowing() && panel.configuration().equals(before), "invalid typed port rejected");
            ((JSpinner.DefaultEditor) port.getEditor()).getTextField().setValue(10001);
            named(dialog, JTextField.class, "connection-tcp-host").setText("127.0.0.1");
            DesktopTheme.setDark(true);
        });
        capture(dialogRef.get(), directory.resolve("connection-dark.png"));
        SwingUtilities.invokeAndWait(() -> {
            button(dialogRef.get(), "Lưu đích TCP").doClick();
            check(panel.configuration().demo() && panel.configuration().tcpPort() == 10001 && panel.configuration().tcpHost().equals("127.0.0.1"), "save TCP ignores source draft and commits typed port");
            DesktopTheme.setDark(false);
        });
        Thread.sleep(300);
        check(panel.monitor.currentSnapshot().startedAt() == start && panel.monitor.currentSnapshot().total() == 34, "TCP-only save preserves data session");
        SwingUtilities.invokeAndWait(() -> {
            panel.openConnection();
            DesktopConnectionDialog dialog = (DesktopConnectionDialog) Arrays.stream(frame.getOwnedWindows()).filter(w -> w instanceof DesktopConnectionDialog && w.isShowing()).findFirst().orElseThrow();
            dialogRef.set(dialog);
        });
        capture(dialogRef.get(), directory.resolve("connection.png"));
        SwingUtilities.invokeAndWait(() -> button(dialogRef.get(), "Áp dụng / phiên mới").doClick());
        await(() -> panel.monitor.currentSnapshot().startedAt() != start && panel.monitor.currentSnapshot().total() == 0, "explicit new session clears monitor data");
        check(panel.selectedPage() == DesktopFrame.Page.LOGIN, "connection changes preserve current screen");
    }
    static void themeChecks(DesktopFrame panel, JFrame frame, Path directory) throws Exception {
        long start = panel.monitor.currentSnapshot().startedAt();
        AtomicReference<JDialog> details = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            check(DesktopTheme.isFlatLaf(), "FlatLaf dependency loaded");
            check(!DesktopTheme.isDark() && Color.WHITE.equals(DesktopTheme.CANVAS), "white default theme");
            JDialog dialog = new JDialog(frame, "Theme detail check");
            dialog.add(new JScrollPane(new JTextArea("Log detail • Tiếng Việt", 8, 30)));
            dialog.pack(); dialog.setVisible(true); details.set(dialog);
            JToggleButton toggle = descendants(panel, JToggleButton.class).stream()
                    .filter(b -> "desktop-theme-toggle".equals(b.getName())).findFirst().orElseThrow();
            toggle.doClick();
            check(DesktopTheme.isDark() && UIManager.getLookAndFeel().getName().contains("Dark"), "theme button activates FlatDarkLaf");
            check(panel.getBackground().equals(DesktopTheme.CANVAS), "existing root panel receives dark surface");
            JTextArea area = descendants(dialog, JTextArea.class).get(0);
            check(area.getBackground().equals(DesktopTheme.SURFACE) && area.getForeground().equals(DesktopTheme.INK), "open details update theme");
            for (DesktopCharts.Traffic chart : descendants(panel, DesktopCharts.Traffic.class))
                check(chart.getBackground().equals(DesktopTheme.SURFACE), "chart dark surface");
            dialog.dispose();
        });
        capture(frame, directory.resolve("dashboard-dark.png"));
        SwingUtilities.invokeAndWait(() -> {
            var input = descendants(panel.screen(DesktopFrame.Page.EVENTS), JTextField.class).get(0); input.setText("VIEW_PRODUCT");
            var toggle = descendants(panel, JToggleButton.class).stream().filter(b -> "desktop-theme-toggle".equals(b.getName())).findFirst().orElseThrow();
            toggle.doClick();
            check(!DesktopTheme.isDark() && panel.getBackground().equals(Color.WHITE), "theme button restores white");
            check(input.getText().equals("VIEW_PRODUCT"), "monitor filter survives theme switch"); input.setText("");
        });
        check(panel.monitor.currentSnapshot().total() == 30 && panel.monitor.currentSnapshot().startedAt() == start,
                "theme retains current demo session data");
    }
    public static void main(String[] args) throws Exception {
        statistics(); parsingAndFixtures(); load(); integration();
        if (args.length == 2 && args[0].equals("--visual")) visual(Path.of(args[1]));
        System.out.println("PASS: " + checks + " checks");
    }
}
