package com.Logcollector.server.UI.desktop;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

final class DesktopMonitor extends JPanel {
    private final DesktopController controller;
    private volatile DesktopStatistics.Snapshot snapshot;
    private final JLabel[] kpis = new JLabel[6];
    private final JLabel rates = DesktopTheme.muted("Hiện tại: — | Trung bình: 0 | Đỉnh: 0 log/giây");
    final JPanel logsPanel = new JPanel(new BorderLayout(0, 12));
    final JPanel eventsPanel = new JPanel(new BorderLayout(0, 12));
    final JPanel alertsPanel = new JPanel(new BorderLayout(0, 12));
    private final JLabel logCount = DesktopTheme.muted("0 log"), groupCount = DesktopTheme.muted("0 nhóm"), alertCount = DesktopTheme.muted("0 thông báo");
    private final DesktopCharts.Traffic traffic = new DesktopCharts.Traffic();
    private final DesktopCharts.Severity severity = new DesktopCharts.Severity();
    private final JComboBox<String> groupLevel = levels(), logLevel = levels();
    private final JComboBox<String> order = new JComboBox<>(new String[]{"Nhiều nhất trước", "Ít nhất trước"});
    private final JTextField groupQuery = new JTextField(22), logQuery = new JTextField(24);
    private final DesktopTheme.Rows<DesktopStatistics.Group> groups = new DesktopTheme.Rows<>(
            new String[]{"Sự kiện", "Severity", "Số lần", "IP liên quan", "Gần nhất"},
            (r, c) -> switch (c) { case 0 -> r.title(); case 1 -> r.severity(); case 2 -> r.count(); case 3 -> r.ips(); default -> r.latest(); });
    private final DesktopTheme.Rows<DesktopLog> logs = logRows();
    private final DesktopTheme.Rows<DesktopStatistics.Alert> alerts = new DesktopTheme.Rows<>(
            new String[]{"Thời gian", "Loại cảnh báo / thông báo", "IP", "Nội dung"},
            (r, c) -> switch (c) { case 0 -> r.time(); case 1 -> r.type(); case 2 -> r.ip(); default -> r.message(); });
    private final DesktopTheme.Rows<DesktopStatistics.BlockedIp> blacklistRows = new DesktopTheme.Rows<>(
            new String[]{"Thời gian khóa", "Địa chỉ IP", "Lý do vi phạm", "Trạng thái"},
            (r, c) -> switch (c) {
                case 0 -> (r.time() == null || r.time().isBlank()) ? "—" : r.time();
                case 1 -> r.ip();
                case 2 -> r.reason();
                default -> "⛔ ĐANG KHÓA";
            });
    private final JTable blacklistTable = DesktopTheme.table(blacklistRows);
    private final JTable alertTable = DesktopTheme.table(alerts);
    private final JTextField alertSearch = new JTextField(18);

    // Top IP panel — Stitch-style bars + hover tooltip
    private List<DesktopStatistics.Ip> displayedIps = List.of();
    private final JTextField ipSearch = new JTextField();
    private final IpBarPanel ipBarPanel = new IpBarPanel();

    DesktopMonitor(DesktopController controller) {
        super(new BorderLayout()); this.controller = controller;
        JPanel content = new DesktopTheme.ScrollContent(); content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(4, 22, 20, 22));
        JPanel cards = new JPanel(new GridLayout(1, 6, 10, 0)); cards.setOpaque(false);
        String[] labels = {"Tổng log nhận", "INFO", "WARNING", "ERROR / CRIT", "Log/giây", "IP • 10.000 log"};
        for (int i = 0; i < kpis.length; i++) { kpis[i] = DesktopTheme.title("0", 27); DesktopTheme.foreground(kpis[i], "blue"); cards.add(DesktopTheme.card(labels[i], "", kpis[i])); }
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 94)); content.add(cards); content.add(Box.createVerticalStrut(12));
        content.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                boolean narrow = content.getWidth() < 1050;
                GridLayout layout = (GridLayout) cards.getLayout();
                int columns = narrow ? 3 : 6;
                if (layout.getColumns() != columns) {
                    layout.setColumns(columns); layout.setRows(narrow ? 2 : 1); layout.setVgap(8);
                    cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, narrow ? 180 : 94));
                    cards.setPreferredSize(new Dimension(700, narrow ? 180 : 94)); content.revalidate();
                }
            }
        });
        JPanel plot = new JPanel(new BorderLayout()); plot.setOpaque(false); plot.add(traffic); plot.add(rates, BorderLayout.SOUTH);
        JPanel trafficCard = DesktopTheme.card("Biến động lưu lượng log", "5 phút gần nhất • trục Y tự mở rộng: đỉnh + tối thiểu 100 log/giây", plot);
        trafficCard.setPreferredSize(new Dimension(1000, 300)); trafficCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        content.add(trafficCard); content.add(Box.createVerticalStrut(12));
        groups.key = DesktopStatistics.Group::key;

        // Top IP panel — Stitch-style
        ipSearch.putClientProperty("JTextField.placeholderText", "Tìm địa chỉ IP…");
        ipSearch.setName("ip-search");
        ipSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterIps(); }
            public void removeUpdate(DocumentEvent e) { filterIps(); }
            public void changedUpdate(DocumentEvent e) { filterIps(); }
        });
        JPanel ipTop = new JPanel(new BorderLayout(0, 8)); ipTop.setOpaque(false);
        ipTop.add(ipSearch, BorderLayout.NORTH);
        JScrollPane ipScroll = new JScrollPane(ipBarPanel);
        ipScroll.setBorder(null); ipScroll.getVerticalScrollBar().setUnitIncrement(18);
        ipScroll.setPreferredSize(new Dimension(440, 170));
        ipTop.add(ipScroll);

        JPanel middle = new JPanel(new GridLayout(1, 2, 12, 0)); middle.setOpaque(false);
        middle.add(DesktopTheme.card("Top IP", "10.000 log gần nhất • rê chuột xem Severity • chọn IP để xem chi tiết", ipTop));
        middle.add(DesktopTheme.card("Phân bố Severity", "Toàn phiên nhận • giữ nguyên mọi mức độ", severity));
        middle.setPreferredSize(new Dimension(1000, 250)); middle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 265));
        content.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                boolean narrow = content.getWidth() < 880;
                GridLayout layout = (GridLayout) middle.getLayout();
                int columns = narrow ? 1 : 2;
                if (layout.getColumns() != columns) {
                    layout.setColumns(columns); layout.setRows(narrow ? 2 : 1); layout.setVgap(12);
                    middle.setPreferredSize(new Dimension(700, narrow ? 480 : 250));
                    middle.setMaximumSize(new Dimension(Integer.MAX_VALUE, narrow ? 500 : 265)); content.revalidate();
                }
            }
        });
        content.add(middle); content.add(Box.createVerticalStrut(12));
        JTable eventTable = DesktopTheme.table(groups), liveTable = DesktopTheme.table(logs);
        activate(eventTable, () -> { int i = eventTable.getSelectedRow(); if (i >= 0 && snapshot != null) controller.details(snapshot, null, groups.rows.get(i).key(), this::showDetails); });
        activate(liveTable, () -> { int i = liveTable.getSelectedRow(); if (i >= 0) showLog(logs.rows.get(i), this); });
        activate(alertTable, () -> { int i = alertTable.getSelectedRow(); if (i >= 0) showAlertDetails(alerts.rows.get(i)); });
        groupQuery.setName("event-query"); logQuery.setName("log-query");
        groupQuery.putClientProperty("JTextField.placeholderText", "Tìm IP hoặc nội dung sự kiện…");
        logQuery.putClientProperty("JTextField.placeholderText", "Tìm IP hoặc nội dung log…");
        eventsPanel.add(filterBar(groupLevel, groupQuery, order), BorderLayout.NORTH);
        eventsPanel.add(DesktopTheme.card("Các nhóm sự kiện", "10.000 log gần nhất • chọn nhóm để xem chi tiết", new JScrollPane(eventTable)));
        eventsPanel.add(groupCount, BorderLayout.SOUTH);
        logsPanel.add(filterBar(logLevel, logQuery, null), BorderLayout.NORTH);
        logsPanel.add(DesktopTheme.card("Log UI đã nhận", "10.000 log gần nhất • mới nhận trước • chọn dòng để đọc đầy đủ", new JScrollPane(liveTable)));
        logsPanel.add(logCount, BorderLayout.SOUTH);

        // Thanh cong cu Tuong lua va tim kiem tren Desktop App
        blacklistRows.key = DesktopStatistics.BlockedIp::ip;
        JPanel firewallControlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        firewallControlBar.setOpaque(false);

        alertSearch.setName("alert-search");
        alertSearch.putClientProperty("JTextField.placeholderText", "🔍 Lọc theo IP / lý do vi phạm / nội dung…");
        alertSearch.setPreferredSize(new Dimension(240, 32));
        alertSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterAlertsAndBlacklist(); }
            public void removeUpdate(DocumentEvent e) { filterAlertsAndBlacklist(); }
            public void changedUpdate(DocumentEvent e) { filterAlertsAndBlacklist(); }
        });

        JButton btnClearSearch = DesktopTheme.button("✕");
        btnClearSearch.setToolTipText("Xóa từ khóa lọc");
        btnClearSearch.addActionListener(e -> alertSearch.setText(""));

        JButton btnViewHistory = DesktopTheme.button("📋 Xem lịch sử vi phạm");
        JButton btnUnblockSelected = DesktopTheme.button("🔓 Bỏ chặn IP đã chọn");
        JButton btnUnblockAll = DesktopTheme.button("⚡ Mở khóa toàn bộ IP");
        JLabel hintLabel = DesktopTheme.muted("Bấm đúp IP để xem chi tiết 5 lần vi phạm • Bấm Bỏ chặn để giải phóng IP");

        firewallControlBar.add(DesktopTheme.muted("Bộ lọc:"));
        firewallControlBar.add(alertSearch);
        firewallControlBar.add(btnClearSearch);
        firewallControlBar.add(btnViewHistory);
        firewallControlBar.add(btnUnblockSelected);
        firewallControlBar.add(btnUnblockAll);
        firewallControlBar.add(hintLabel);

        btnUnblockSelected.addActionListener(e -> {
            String ip = getSelectedFirewallIp();
            if (ip == null) {
                JOptionPane.showMessageDialog(this,
                        "Vui lòng chọn một dòng có chứa địa chỉ IP trong bảng Danh sách đen hoặc bảng Cảnh báo!",
                        "Chưa chọn IP", JOptionPane.WARNING_MESSAGE);
                return;
            }
            doUnblockIp(ip);
        });

        btnUnblockAll.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn có chắc chắn muốn mở khóa toàn bộ các IP trong Danh sách đen Tường lửa không?",
                    "Xác nhận mở khóa toàn bộ", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            boolean ok = controller.unblockAllIps();
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Đã gửi lệnh mở khóa toàn bộ IP tới Tường lửa Server thành công!\nToàn bộ danh sách đen Tường lửa trên Server đã được giải phóng.",
                        "Gỡ chặn toàn bộ", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Không thể gửi lệnh mở khóa tới Server. Vui lòng kiểm tra kết nối!",
                        "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnViewHistory.addActionListener(e -> {
            String ip = getSelectedFirewallIp();
            if (ip == null) {
                String input = JOptionPane.showInputDialog(this,
                        "Chọn một IP từ bảng hoặc nhập địa chỉ IP cần xem lịch sử vi phạm:",
                        "Xem lịch sử vi phạm", JOptionPane.QUESTION_MESSAGE);
                if (input != null && !input.isBlank()) {
                    ip = input.trim();
                } else {
                    return;
                }
            }
            showViolationHistory(ip);
        });

        activate(blacklistTable, () -> {
            int i = blacklistTable.getSelectedRow();
            if (i >= 0 && i < blacklistRows.getRowCount()) {
                showViolationHistory(blacklistRows.rows.get(i).ip());
            }
        });

        JPopupMenu blackPopup = new JPopupMenu();
        JMenuItem miUnblockBlack = new JMenuItem("🔓 Bỏ chặn IP này khỏi Tường lửa");
        JMenuItem miHistoryBlack = new JMenuItem("📋 Xem chi tiết lịch sử vi phạm");
        JMenuItem miFilterBlack = new JMenuItem("🔍 Lọc các bản tin theo IP này");
        blackPopup.add(miUnblockBlack);
        blackPopup.add(miHistoryBlack);
        blackPopup.addSeparator();
        blackPopup.add(miFilterBlack);

        miUnblockBlack.addActionListener(e -> {
            int i = blacklistTable.getSelectedRow();
            if (i >= 0 && i < blacklistRows.getRowCount()) {
                doUnblockIp(blacklistRows.rows.get(i).ip());
            }
        });
        miHistoryBlack.addActionListener(e -> {
            int i = blacklistTable.getSelectedRow();
            if (i >= 0 && i < blacklistRows.getRowCount()) {
                showViolationHistory(blacklistRows.rows.get(i).ip());
            }
        });
        miFilterBlack.addActionListener(e -> {
            int i = blacklistTable.getSelectedRow();
            if (i >= 0 && i < blacklistRows.getRowCount()) {
                alertSearch.setText(blacklistRows.rows.get(i).ip());
            }
        });
        blacklistTable.setComponentPopupMenu(blackPopup);

        JPopupMenu alertPopup = new JPopupMenu();
        JMenuItem miUnblockAlert = new JMenuItem("🔓 Bỏ chặn IP này khỏi Tường lửa");
        JMenuItem miHistoryAlert = new JMenuItem("📋 Xem chi tiết lịch sử vi phạm");
        JMenuItem miFilterAlert = new JMenuItem("🔍 Lọc các bản tin theo IP này");
        alertPopup.add(miUnblockAlert);
        alertPopup.add(miHistoryAlert);
        alertPopup.addSeparator();
        alertPopup.add(miFilterAlert);

        miUnblockAlert.addActionListener(e -> {
            int i = alertTable.getSelectedRow();
            if (i >= 0 && i < alerts.getRowCount()) {
                String ip = alerts.rows.get(i).ip();
                if (hasValidIp(ip)) doUnblockIp(ip);
            }
        });
        miHistoryAlert.addActionListener(e -> {
            int i = alertTable.getSelectedRow();
            if (i >= 0 && i < alerts.getRowCount()) {
                String ip = alerts.rows.get(i).ip();
                if (hasValidIp(ip)) showViolationHistory(ip);
            }
        });
        miFilterAlert.addActionListener(e -> {
            int i = alertTable.getSelectedRow();
            if (i >= 0 && i < alerts.getRowCount()) {
                String ip = alerts.rows.get(i).ip();
                if (hasValidIp(ip)) alertSearch.setText(ip);
            }
        });
        alertTable.setComponentPopupMenu(alertPopup);

        blacklistTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        blacklistTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        blacklistTable.getColumnModel().getColumn(2).setPreferredWidth(210);
        blacklistTable.getColumnModel().getColumn(3).setPreferredWidth(100);

        alertTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        alertTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        alertTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        alertTable.getColumnModel().getColumn(3).setPreferredWidth(320);

        JPanel blacklistCard = DesktopTheme.card("🛡️ Danh sách đen Tường lửa (Blacklist)",
                "Các IP vi phạm bị giam giữ trong BLOCKED_IPS • Click đúp để xem chi tiết vi phạm",
                new JScrollPane(blacklistTable));
        blacklistCard.setMinimumSize(new Dimension(380, 200));

        JPanel alertCard = DesktopTheme.card("⚠️ Nhật ký Cảnh báo hệ thống",
                "Tối đa 1.000 bản tin an ninh từ Server • Click đúp để đọc chi tiết hoặc Bỏ chặn",
                new JScrollPane(alertTable));
        alertCard.setMinimumSize(new Dimension(420, 200));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, blacklistCard, alertCard);
        splitPane.setResizeWeight(0.42);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        alertsPanel.add(firewallControlBar, BorderLayout.NORTH);
        alertsPanel.add(splitPane, BorderLayout.CENTER);
        alertsPanel.add(alertCount, BorderLayout.SOUTH);
        for (JPanel page : new JPanel[]{logsPanel, eventsPanel, alertsPanel}) page.setBorder(BorderFactory.createEmptyBorder(4, 22, 12, 22));
        liveTable.getColumnModel().getColumn(4).setPreferredWidth(360);
        eventTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        JScrollPane scroll = new JScrollPane(content); scroll.getVerticalScrollBar().setUnitIncrement(24); scroll.setBorder(null); add(scroll);
        DocumentListener change = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filters(); } public void removeUpdate(DocumentEvent e) { filters(); } public void changedUpdate(DocumentEvent e) { filters(); }
        };
        groupQuery.getDocument().addDocumentListener(change); logQuery.getDocument().addDocumentListener(change);
        groupLevel.addActionListener(e -> filters()); logLevel.addActionListener(e -> filters()); order.addActionListener(e -> filters());
    }

    /** Filter the displayed IP list by search text; preserves ranking order. */
    private void filterIps() {
        if (snapshot == null) return;
        String q = ipSearch.getText().trim().toLowerCase(Locale.ROOT);
        displayedIps = q.isEmpty() ? snapshot.ips()
                : snapshot.ips().stream().filter(ip -> ip.address().toLowerCase(Locale.ROOT).contains(q)).toList();
        ipBarPanel.setIps(displayedIps, snapshot.ips().isEmpty() ? 1 : snapshot.ips().get(0).count());
    }

    private static JPanel filterBar(JComboBox<String> severity, JTextField query, JComboBox<String> order) {
        JPanel filter = new JPanel(new GridBagLayout()); filter.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints(); c.gridy = 0; c.gridx = 0; c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 6, 12); filter.add(DesktopTheme.muted("Severity"), c);
        c.gridx = 1; filter.add(DesktopTheme.muted("Tìm theo IP / nội dung"), c);
        if (order != null) { c.gridx = 2; filter.add(DesktopTheme.muted("Sắp xếp số lần"), c); }
        c.gridy = 1; c.gridx = 0; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(0, 0, 0, 12); filter.add(severity, c);
        c.gridx = 1; c.weightx = 1; query.setMinimumSize(new Dimension(100, 30)); filter.add(query, c);
        if (order != null) { c.gridx = 2; c.weightx = 0; c.insets = new Insets(0, 0, 0, 0); filter.add(order, c); }
        return filter;
    }
    private void filters() {
        controller.filters(new DesktopStatistics.Filter((String) groupLevel.getSelectedItem(), groupQuery.getText().trim(), order.getSelectedIndex() == 1),
                new DesktopStatistics.Filter((String) logLevel.getSelectedItem(), logQuery.getText().trim(), false));
    }
    void update(DesktopController.View view) {
        snapshot = view.data();
        long errorOrCrit = snapshot.severity().getOrDefault("ERROR", 0L)
                + snapshot.severity().getOrDefault("CRITICAL", 0L)
                + snapshot.severity().getOrDefault("ALERT", 0L)
                + snapshot.severity().getOrDefault("EMERGENCY", 0L);
        long[] values = {snapshot.total(), snapshot.severity().getOrDefault("INFO", 0L), snapshot.severity().getOrDefault("WARNING", 0L), errorOrCrit, snapshot.current() == null ? 0 : snapshot.current(), snapshot.ipCount()};
        for (int i = 0; i < kpis.length; i++) kpis[i].setText(i == 4 && snapshot.current() == null ? "—" : String.format("%,d", values[i]));
        traffic.update(snapshot); severity.update(snapshot.severity());
        rates.setText(String.format("Hiện tại: %s  |  Trung bình: %,.2f  |  Đỉnh: %,d log/giây  |  Trục Y: 0–%,.0f", snapshot.current() == null ? "—" : snapshot.current(), snapshot.average(), snapshot.peak(), snapshot.axisMax()));
        // Re-filter IPs preserving search text
        filterIps();
        groups.set(snapshot.groups());
        logs.set(snapshot.logs());
        filterAlertsAndBlacklist();
        logCount.setText(logs.getRowCount() + " log phù hợp / " + snapshot.retained() + " log đang giữ • Bộ lọc không thay đổi KPI");
        groupCount.setText(groups.getRowCount() + " nhóm phù hợp • Số lần và chi tiết tính trên toàn bộ nhóm");
    }
    DesktopStatistics.Snapshot currentSnapshot() { return snapshot; }
    private static JComboBox<String> levels() { return new JComboBox<>(new String[]{"Tất cả", "INFO", "WARNING", "ERROR", "CRITICAL", "ALERT", "EMERGENCY", "NOTICE", "DEBUG", "UNKNOWN"}); }
    static DesktopTheme.Rows<DesktopLog> logRows() {
        return new DesktopTheme.Rows<>(new String[]{"Timestamp", "IP nguồn", "Severity", "Sự kiện", "Nội dung"},
                (r, c) -> switch (c) { case 0 -> r.event().getTimestamp(); case 1 -> r.event().getIp(); case 2 -> r.event().getSeverityLevel(); case 3 -> r.eventType(); default -> r.event().getMessage(); });
    }
    static void activate(JTable table, Runnable action) {
        table.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { if (e.getButton() == MouseEvent.BUTTON1 && table.rowAtPoint(e.getPoint()) >= 0) action.run(); } });
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "details");
        table.getActionMap().put("details", new AbstractAction() { public void actionPerformed(ActionEvent e) { action.run(); } });
    }
    private void showDetails(List<DesktopLog> records) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Chi tiết • " + records.size() + " log (snapshot)", Dialog.ModalityType.MODELESS);
        var model = logRows(); model.set(records); JTable table = DesktopTheme.table(model);
        activate(table, () -> { int i = table.getSelectedRow(); if (i >= 0) showLog(model.rows.get(i), dialog); });
        dialog.add(new JScrollPane(table)); dialog.setSize(1080, 460); dialog.setLocationRelativeTo(this); dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); dialog.setVisible(true);
    }
    static void showLog(DesktopLog log, Component parent) {
        var e = log.event();
        showText("Chi tiết log • " + log.source(), "Timestamp: " + e.getTimestamp() + "\nIP: " + e.getIp() + "\nSeverity: " + e.getSeverityLevel()
                + "\nSự kiện: " + log.eventType() + "\nThiết bị: " + e.getDevice() + "\nCategory: " + e.getCategory()
                + "\nFacility: " + (log.raw().isBlank() ? "Không được Server cung cấp" : e.getFacility())
                + "\n\nNội dung:\n" + e.getMessage() + "\n\nRaw:\n" + log.raw(), parent);
    }
    private void showAlertDetails(DesktopStatistics.Alert alert) {
        String ip = alert.ip();
        boolean hasIp = ip != null && !ip.isBlank() && !"—".equals(ip) && !"-".equals(ip);
        boolean isBlocked = hasIp && controller.isIpBlocked(ip);

        StringBuilder sb = new StringBuilder();
        sb.append("Thời gian: ").append(alert.time()).append("\n");
        sb.append("Loại cảnh báo: ").append(alert.type()).append("\n");
        sb.append("Địa chỉ IP: ").append(hasIp ? ip : "Không có").append("\n");
        sb.append("Trạng thái Tường lửa: ").append(isBlocked ? "ĐANG BỊ KHÓA (BLOCKED)" : "Bình thường / Chưa bị khóa").append("\n\n");
        sb.append("Nội dung chi tiết:\n").append(alert.message());

        JTextArea area = new JTextArea(sb.toString(), 12, 60);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JScrollPane(area), BorderLayout.CENTER);

        if (hasIp) {
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton btnHistory = DesktopTheme.button("📋 Xem lịch sử vi phạm");
            JButton btnUnblock = DesktopTheme.button(isBlocked ? "🔓 Mở khóa / Bỏ chặn IP này" : "🔓 Bỏ chặn IP này khỏi Tường lửa");
            btnPanel.add(btnHistory);
            btnPanel.add(btnUnblock);
            panel.add(btnPanel, BorderLayout.SOUTH);

            btnHistory.addActionListener(e -> {
                Window w = SwingUtilities.getWindowAncestor(btnHistory);
                if (w != null) w.dispose();
                showViolationHistory(ip);
            });
            btnUnblock.addActionListener(e -> {
                boolean ok = controller.unblockIp(ip);
                if (ok) {
                    JOptionPane.showMessageDialog(this, "Đã mở khóa / bỏ chặn IP " + ip + " thành công!", "Bỏ chặn thành công", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "IP " + ip + " hiện không nằm trong danh sách đen hoặc đã được mở khóa trước đó.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        }

        JOptionPane.showMessageDialog(this, panel, "Chi tiết cảnh báo", JOptionPane.PLAIN_MESSAGE);
    }

    private void filterAlertsAndBlacklist() {
        if (snapshot == null) return;
        String q = alertSearch.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            blacklistRows.set(snapshot.blockedIps());
            alerts.set(snapshot.alerts());
        } else {
            blacklistRows.set(snapshot.blockedIps().stream()
                    .filter(b -> b.ip().toLowerCase(Locale.ROOT).contains(q)
                            || b.reason().toLowerCase(Locale.ROOT).contains(q))
                    .toList());
            alerts.set(snapshot.alerts().stream()
                    .filter(a -> (a.ip() != null && a.ip().toLowerCase(Locale.ROOT).contains(q))
                            || (a.type() != null && a.type().toLowerCase(Locale.ROOT).contains(q))
                            || (a.message() != null && a.message().toLowerCase(Locale.ROOT).contains(q)))
                    .toList());
        }
        alertCount.setText(alerts.getRowCount() + " cảnh báo / thông báo • "
                + blacklistRows.getRowCount() + " IP trong danh sách đen Tường lửa"
                + (q.isEmpty() ? "" : " (Đang áp dụng bộ lọc: \"" + q + "\")"));
    }

    private String getSelectedFirewallIp() {
        int blackIdx = blacklistTable.getSelectedRow();
        if (blackIdx >= 0 && blackIdx < blacklistRows.getRowCount()) {
            String ip = blacklistRows.rows.get(blackIdx).ip();
            if (hasValidIp(ip)) return ip.trim();
        }
        int alertIdx = alertTable.getSelectedRow();
        if (alertIdx >= 0 && alertIdx < alerts.getRowCount()) {
            String ip = alerts.rows.get(alertIdx).ip();
            if (hasValidIp(ip)) return ip.trim();
        }
        return null;
    }

    private static boolean hasValidIp(String ip) {
        return ip != null && !ip.isBlank() && !"—".equals(ip) && !"-".equals(ip) && !"TẤT CẢ IP".equals(ip) && !"ALL".equalsIgnoreCase(ip);
    }

    private void doUnblockIp(String ip) {
        if (!hasValidIp(ip)) {
            JOptionPane.showMessageDialog(this, "Dòng được chọn không chứa địa chỉ IP hợp lệ!", "Không có IP", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean ok = controller.unblockIp(ip);
        if (ok) {
            JOptionPane.showMessageDialog(this,
                    "Đã gửi lệnh gỡ chặn IP " + ip + " tới Tường lửa Server!\nIP này đã được mở khóa và xóa khỏi danh sách đen.",
                    "Bỏ chặn thành công", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Không thể gửi lệnh gỡ chặn tới Server. Vui lòng kiểm tra kết nối!",
                    "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        }
    }

    void showViolationHistory(String targetIp) {
        if (!hasValidIp(targetIp)) {
            JOptionPane.showMessageDialog(this, "Địa chỉ IP không hợp lệ!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String cleanIp = targetIp.trim();
        List<DesktopLog> allLogs = (snapshot != null) ? snapshot.allLogs() : List.of();

        List<DesktopLog> ipLogs = allLogs.stream()
                .filter(l -> cleanIp.equalsIgnoreCase(l.event().getIp()))
                .toList();

        List<DesktopLog> violations = ipLogs.stream()
                .filter(l -> {
                    String msg = l.event().getMessage() != null ? l.event().getMessage().toLowerCase(Locale.ROOT) : "";
                    String sev = l.event().getSeverityLevel() != null ? l.event().getSeverityLevel() : "";
                    return msg.contains("failed password") || msg.contains("invalid user")
                            || msg.contains("authentication failure") || msg.contains("break-in")
                            || "WARNING".equalsIgnoreCase(sev) || "CRITICAL".equalsIgnoreCase(sev) || "ERROR".equalsIgnoreCase(sev);
                })
                .toList();

        List<DesktopLog> displayList = violations.isEmpty() ? ipLogs : violations;
        boolean isBlocked = controller.isIpBlocked(cleanIp);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Chi tiết lịch sử vi phạm Tường lửa • IP " + cleanIp, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(0, 12));

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(14, 18, 6, 18));
        DesktopTheme.surface(headerPanel);

        JPanel titleRow = new JPanel(new BorderLayout(12, 0));
        titleRow.setOpaque(false);
        JLabel lblIp = DesktopTheme.title("IP: " + cleanIp, 18);
        DesktopTheme.foreground(lblIp, "blue");

        JLabel lblStatus = new JLabel(isBlocked ? "⛔ ĐANG BỊ KHÓA TRONG TƯỜNG LỬA" : "✅ Trạng thái: Bình thường / Chưa bị khóa");
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.BOLD, 13f));
        lblStatus.setForeground(isBlocked ? DesktopTheme.severity("CRITICAL") : DesktopTheme.severity("INFO"));
        titleRow.add(lblIp, BorderLayout.WEST);
        titleRow.add(lblStatus, BorderLayout.EAST);
        headerPanel.add(titleRow);

        headerPanel.add(Box.createVerticalStrut(6));
        String summaryText = violations.isEmpty()
                ? "Không phát hiện log lỗi xác thực đặc thù. Hiển thị " + displayList.size() + " log ghi nhận từ IP này."
                : "Phát hiện " + violations.size() + " lần thử sai mật khẩu / vi phạm an ninh (Ngưỡng tự động khóa là 5 lần vi phạm liên tiếp)";
        JLabel lblSummary = DesktopTheme.muted(summaryText);
        headerPanel.add(lblSummary);

        record ViolationEntry(int order, String time, String userAndPort, String severity, String message) {}
        List<ViolationEntry> entries = new ArrayList<>();
        for (int idx = 0; idx < displayList.size(); idx++) {
            DesktopLog dl = displayList.get(idx);
            String msg = dl.event().getMessage() != null ? dl.event().getMessage() : "";
            String userAndPort = extractUserAndPort(msg);
            entries.add(new ViolationEntry(idx + 1, dl.event().getTimestamp(), userAndPort, dl.event().getSeverityLevel(), msg));
        }

        DesktopTheme.Rows<ViolationEntry> violationRows = new DesktopTheme.Rows<>(
                new String[]{"# Lần vi phạm", "Thời gian", "Tài khoản đích / Cổng", "Mức độ", "Chi tiết thông điệp Server"},
                (r, c) -> switch (c) {
                    case 0 -> (r.order == 5) ? "#5 ⚠️ (Ngưỡng khóa)" : "#" + r.order;
                    case 1 -> r.time;
                    case 2 -> r.userAndPort;
                    case 3 -> r.severity;
                    default -> r.message;
                }
        );
        violationRows.set(entries);
        JTable violationTable = DesktopTheme.table(violationRows);
        violationTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        violationTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        violationTable.getColumnModel().getColumn(2).setPreferredWidth(180);
        violationTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        violationTable.getColumnModel().getColumn(4).setPreferredWidth(480);

        activate(violationTable, () -> {
            int sel = violationTable.getSelectedRow();
            if (sel >= 0 && sel < displayList.size()) {
                showLog(displayList.get(sel), dialog);
            }
        });

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 18, 14, 18));
        bottomBar.setOpaque(false);

        JLabel hint = DesktopTheme.muted("Bấm đúp vào dòng để xem chi tiết log đầy đủ");
        bottomBar.add(hint, BorderLayout.WEST);

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnGroup.setOpaque(false);
        JButton btnUnblockNow = DesktopTheme.button(isBlocked ? "🔓 Mở khóa / Bỏ chặn IP này ngay" : "🔓 Gỡ bỏ IP khỏi Tường lửa");
        JButton btnClose = DesktopTheme.button("Đóng");

        btnGroup.add(btnUnblockNow);
        btnGroup.add(btnClose);
        bottomBar.add(btnGroup, BorderLayout.EAST);

        btnUnblockNow.addActionListener(e -> {
            boolean ok = controller.unblockIp(cleanIp);
            if (ok) {
                lblStatus.setText("✅ Đã mở khóa thành công");
                lblStatus.setForeground(DesktopTheme.severity("INFO"));
                btnUnblockNow.setEnabled(false);
                JOptionPane.showMessageDialog(dialog, "Đã mở khóa IP " + cleanIp + " thành công!", "Bỏ chặn thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(dialog, "Không thể gửi lệnh mở khóa tới Server. Vui lòng kiểm tra kết nối!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnClose.addActionListener(e -> dialog.dispose());

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        centerPanel.add(new JScrollPane(violationTable), BorderLayout.CENTER);

        dialog.add(headerPanel, BorderLayout.NORTH);
        dialog.add(centerPanel, BorderLayout.CENTER);
        dialog.add(bottomBar, BorderLayout.SOUTH);

        dialog.setSize(1080, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
    }

    private static String extractUserAndPort(String msg) {
        if (msg == null || msg.isBlank()) return "—";
        String lower = msg.toLowerCase(Locale.ROOT);
        String user = "";
        String port = "";
        if (lower.contains("for invalid user ")) {
            int start = lower.indexOf("for invalid user ") + "for invalid user ".length();
            int end = lower.indexOf(" from", start);
            user = (end > start) ? msg.substring(start, end).trim() + " (Invalid)" : "";
        } else if (lower.contains("for illegal user ")) {
            int start = lower.indexOf("for illegal user ") + "for illegal user ".length();
            int end = lower.indexOf(" from", start);
            user = (end > start) ? msg.substring(start, end).trim() + " (Illegal)" : "";
        } else if (lower.contains("for ")) {
            int start = lower.indexOf("for ") + "for ".length();
            int end = lower.indexOf(" from", start);
            user = (end > start) ? msg.substring(start, end).trim() : "";
        }
        if (lower.contains("port ")) {
            int start = lower.indexOf("port ") + "port ".length();
            int end = lower.indexOf(" ", start);
            if (end < 0) end = lower.length();
            port = msg.substring(start, end).trim();
        }
        if (!user.isEmpty() && !port.isEmpty()) return user + " (port " + port + ")";
        if (!user.isEmpty()) return user;
        if (!port.isEmpty()) return "port " + port;
        return "—";
    }

    static void showText(String title, String text, Component parent) {
        JTextArea area = new JTextArea(text, 18, 75); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setCaretPosition(0);
        JOptionPane.showMessageDialog(parent, new JScrollPane(area), title, JOptionPane.PLAIN_MESSAGE);
    }

    // ── Stitch-style Top IP bar panel ────────────────────────────────────────
    /** Custom panel: one row per IP — thin coloured progress bar + severity tooltip on hover. */
    final class IpBarPanel extends JPanel implements Scrollable {
        private List<DesktopStatistics.Ip> rows = List.of();
        private long maxCount = 1;
        private static final int ROW_H = 46, PAD_X = 10, BAR_H = 4, BAR_Y_OFFSET = 30;
        private int hoveredRow = -1;
        private Popup activeTooltip;

        IpBarPanel() {
            setOpaque(false);
            setName("ip-bar-panel");
            MouseAdapter ma = new MouseAdapter() {
                public void mouseMoved(MouseEvent e) { updateHover(e.getPoint()); }
                public void mouseExited(MouseEvent e) { clearHover(); }
                public void mouseClicked(MouseEvent e) { handleClick(e); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void setIps(List<DesktopStatistics.Ip> list, long max) {
            rows = list; maxCount = Math.max(1, max);
            setPreferredSize(new Dimension(0, Math.max(1, rows.size()) * ROW_H));
            revalidate(); repaint();
        }

        private void updateHover(Point p) {
            int row = p.y / ROW_H;
            if (row != hoveredRow) { hoveredRow = (row >= 0 && row < rows.size()) ? row : -1; repaint(); }
            hideTooltip();
            if (hoveredRow >= 0) showTooltip(hoveredRow, p);
        }
        private void clearHover() { hoveredRow = -1; repaint(); hideTooltip(); }

        private void hideTooltip() { if (activeTooltip != null) { activeTooltip.hide(); activeTooltip = null; } }

        private void showTooltip(int row, Point mouseInPanel) {
            DesktopStatistics.Ip ip = rows.get(row);
            JPanel tip = buildTooltipPanel(ip);
            Point screen = mouseInPanel.getLocation();
            SwingUtilities.convertPointToScreen(screen, this);
            // Position above/below depending on space
            Dimension tipSize = tip.getPreferredSize();
            int tx = screen.x + 14;
            int ty = screen.y - tipSize.height - 6;
            if (ty < 0) ty = screen.y + 18;
            activeTooltip = PopupFactory.getSharedInstance().getPopup(this, tip, tx, ty);
            activeTooltip.show();
        }

        /** Build the floating tooltip panel for one IP entry. */
        private JPanel buildTooltipPanel(DesktopStatistics.Ip ip) {
            JPanel panel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(DesktopTheme.SURFACE);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(DesktopTheme.BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    g2.dispose();
                }
            };
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

            // Header: IP + total
            JPanel header = new JPanel(new BorderLayout(16, 0)); header.setOpaque(false);
            JLabel ipLabel = new JLabel(ip.address());
            ipLabel.setFont(ipLabel.getFont().deriveFont(Font.BOLD, 13f));
            ipLabel.setForeground(DesktopTheme.INK);
            JLabel total = new JLabel(String.format("%,d log", ip.count()));
            total.setFont(total.getFont().deriveFont(Font.PLAIN, 12f));
            total.setForeground(DesktopTheme.MUTED);
            header.add(ipLabel, BorderLayout.WEST); header.add(total, BorderLayout.EAST);
            panel.add(header);

            // Divider
            panel.add(Box.createVerticalStrut(6));
            JSeparator sep = new JSeparator(); sep.setForeground(DesktopTheme.BORDER); sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            panel.add(sep);
            panel.add(Box.createVerticalStrut(6));

            // Severity rows — sorted by count descending
            Map<String, Long> sev = ip.severity();
            if (sev.isEmpty()) {
                JLabel none = new JLabel("Không có dữ liệu Severity");
                none.setForeground(DesktopTheme.MUTED); none.setFont(none.getFont().deriveFont(11f));
                panel.add(none);
            } else {
                sev.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            panel.add(severityRow(entry.getKey(), entry.getValue(), ip.count()));
                            panel.add(Box.createVerticalStrut(4));
                        });
            }

            // Footer: latest time
            if (!ip.latestTime().isBlank()) {
                panel.add(Box.createVerticalStrut(6));
                JSeparator sep2 = new JSeparator(); sep2.setForeground(DesktopTheme.BORDER); sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                panel.add(sep2); panel.add(Box.createVerticalStrut(6));
                JLabel latest = new JLabel("Gần nhất: " + ip.latestTime());
                latest.setFont(latest.getFont().deriveFont(Font.PLAIN, 11f));
                latest.setForeground(DesktopTheme.MUTED);
                panel.add(latest);
            }
            return panel;
        }

        /** One line: ● SEVERITY    count   (pct%) */
        private JPanel severityRow(String level, long count, long total) {
            JPanel row = new JPanel(new BorderLayout(8, 0)); row.setOpaque(false);
            // Dot + label
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); left.setOpaque(false);
            left.add(new SeverityDot(level));
            JLabel name = new JLabel(level);
            name.setFont(name.getFont().deriveFont(Font.PLAIN, 12f));
            name.setForeground(DesktopTheme.severity(level));
            left.add(name);
            // count + pct
            double pct = total == 0 ? 0 : count * 100.0 / total;
            JLabel right = new JLabel(String.format("%,d  (%.1f%%)", count, pct));
            right.setFont(right.getFont().deriveFont(Font.PLAIN, 12f));
            right.setForeground(DesktopTheme.MUTED);
            row.add(left, BorderLayout.WEST); row.add(right, BorderLayout.EAST);
            return row;
        }

        private void handleClick(MouseEvent e) {
            int row = e.getY() / ROW_H;
            if (e.getButton() == MouseEvent.BUTTON1 && row >= 0 && row < rows.size() && snapshot != null) {
                controller.details(snapshot, rows.get(row).address(), null,
                        logs -> { hideTooltip(); showDetailsForIp(logs); });
            }
        }

        protected void paintComponent(Graphics original) {
            super.paintComponent(original);
            Graphics2D g = (Graphics2D) original.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            try {
                int w = getWidth();
                long total = rows.isEmpty() ? 1 : maxCount;
                for (int i = 0; i < rows.size(); i++) {
                    DesktopStatistics.Ip ip = rows.get(i);
                    int y = i * ROW_H;
                    // Hover highlight
                    if (i == hoveredRow) {
                        g.setColor(DesktopTheme.STRIPE);
                        g.fillRoundRect(0, y + 2, w, ROW_H - 4, 8, 8);
                    }
                    // IP address text
                    g.setFont(original.getFont().deriveFont(Font.BOLD, 13f));
                    g.setColor(DesktopTheme.INK);
                    g.drawString(ip.address(), PAD_X, y + 17);
                    // % • count — right aligned
                    long ipTotal = rows.stream().mapToLong(DesktopStatistics.Ip::count).sum();
                    double pct = ipTotal == 0 ? 0 : ip.count() * 100.0 / ipTotal;
                    String meta = String.format("%.0f%%  •  %,d log", pct, ip.count());
                    g.setFont(original.getFont().deriveFont(Font.PLAIN, 11f));
                    g.setColor(DesktopTheme.MUTED);
                    int metaW = g.getFontMetrics().stringWidth(meta);
                    g.drawString(meta, w - PAD_X - metaW, y + 17);
                    // Progress bar — thin, rank-coloured
                    int barMaxW = w - PAD_X * 2;
                    int barW = (int) (ip.count() * barMaxW / total);
                    g.setColor(DesktopTheme.BORDER);
                    g.fillRoundRect(PAD_X, y + BAR_Y_OFFSET, barMaxW, BAR_H, BAR_H, BAR_H);
                    g.setColor(rankColor(i));
                    g.fillRoundRect(PAD_X, y + BAR_Y_OFFSET, Math.max(BAR_H, barW), BAR_H, BAR_H, BAR_H);
                }
                if (rows.isEmpty()) {
                    g.setFont(original.getFont().deriveFont(Font.PLAIN, 13f));
                    g.setColor(DesktopTheme.MUTED);
                    String msg = "Chưa có dữ liệu phù hợp";
                    g.drawString(msg, Math.max(12, (w - g.getFontMetrics().stringWidth(msg)) / 2), 34);
                }
            } finally { g.dispose(); }
        }

        /** Bar colour cycles through theme palette by rank. */
        private Color rankColor(int rank) {
            return switch (rank % 4) {
                case 0 -> DesktopTheme.severity("INFO");
                case 1 -> DesktopTheme.BLUE;
                case 2 -> DesktopTheme.severity("WARNING");
                default -> DesktopTheme.MUTED;
            };
        }

        // Scrollable — tracks viewport width, natural height
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle v, int o, int d) { return ROW_H; }
        public int getScrollableBlockIncrement(Rectangle v, int o, int d) { return Math.max(ROW_H, v.height - ROW_H); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** 8×8 filled circle in severity colour, used in the hover tooltip. */
    private static final class SeverityDot extends JComponent {
        private final String level;
        SeverityDot(String level) { this.level = level; setPreferredSize(new Dimension(8, 8)); setOpaque(false); }
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(DesktopTheme.severity(level));
            g2.fillOval(0, 0, 8, 8);
            g2.dispose();
        }
    }

    private void showDetailsForIp(List<DesktopLog> records) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Chi tiết • " + records.size() + " log (snapshot)", Dialog.ModalityType.MODELESS);
        var model = logRows(); model.set(records); JTable table = DesktopTheme.table(model);
        activate(table, () -> { int i = table.getSelectedRow(); if (i >= 0) showLog(model.rows.get(i), dialog); });
        dialog.add(new JScrollPane(table)); dialog.setSize(1080, 460); dialog.setLocationRelativeTo(this); dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); dialog.setVisible(true);
    }
}
