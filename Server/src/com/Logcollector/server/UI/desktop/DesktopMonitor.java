package com.Logcollector.server.UI.desktop;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

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
    private final DesktopTheme.Rows<DesktopStatistics.Ip> ips = new DesktopTheme.Rows<>(new String[]{"IP nguồn", "Số log", "Tỷ lệ"},
            (r, c) -> c == 0 ? r.address() : r.count());
    private final DesktopTheme.Rows<DesktopStatistics.Group> groups = new DesktopTheme.Rows<>(
            new String[]{"Sự kiện", "Severity", "Số lần", "IP liên quan", "Gần nhất"},
            (r, c) -> switch (c) { case 0 -> r.title(); case 1 -> r.severity(); case 2 -> r.count(); case 3 -> r.ips(); default -> r.latest(); });
    private final DesktopTheme.Rows<DesktopLog> logs = logRows();
    private final DesktopTheme.Rows<DesktopStatistics.Alert> alerts = new DesktopTheme.Rows<>(
            new String[]{"Thời gian", "Loại cảnh báo / thông báo", "IP", "Nội dung"},
            (r, c) -> switch (c) { case 0 -> r.time(); case 1 -> r.type(); case 2 -> r.ip(); default -> r.message(); });

    DesktopMonitor(DesktopController controller) {
        super(new BorderLayout()); this.controller = controller;
        JPanel content = new DesktopTheme.ScrollContent(); content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(4, 22, 20, 22));
        JPanel cards = new JPanel(new GridLayout(1, 6, 10, 0)); cards.setOpaque(false);
        String[] labels = {"Tổng log nhận", "INFO", "WARNING", "ERROR", "Log/giây", "IP • 10.000 log"};
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
        JTable ipTable = DesktopTheme.table(ips);
        ips.key = DesktopStatistics.Ip::address;
        groups.key = DesktopStatistics.Group::key;
        ipTable.getColumnModel().getColumn(2).setCellRenderer((table, value, selected, focus, row, column) -> {
            JProgressBar bar = new JProgressBar(0, 1000);
            long max = ips.rows.isEmpty() ? 1 : ips.rows.get(0).count();
            bar.setValue((int) (((Number) value).longValue() * 1000 / Math.max(1, max))); bar.setForeground(DesktopTheme.BLUE);
            bar.setStringPainted(true); bar.setString(Math.round(((Number) value).longValue() * 100.0 / Math.max(1, max)) + "%"); return bar;
        });
        activate(ipTable, () -> { int i = ipTable.getSelectedRow(); if (i >= 0 && snapshot != null) controller.details(snapshot, ips.rows.get(i).address(), null, this::showDetails); });
        JScrollPane ipScroll = new JScrollPane(ipTable); ipScroll.setPreferredSize(new Dimension(470, 160));
        JPanel middle = new JPanel(new GridLayout(1, 2, 12, 0)); middle.setOpaque(false);
        middle.add(DesktopTheme.card("Top IP", "10.000 log gần nhất • chọn IP để xem chi tiết", ipScroll));
        middle.add(DesktopTheme.card("Phân bố Severity", "Toàn phiên nhận • giữ nguyên mọi mức độ", severity));
        middle.setPreferredSize(new Dimension(1000, 230)); middle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 245));
        content.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                boolean narrow = content.getWidth() < 880;
                GridLayout layout = (GridLayout) middle.getLayout();
                int columns = narrow ? 1 : 2;
                if (layout.getColumns() != columns) {
                    layout.setColumns(columns); layout.setRows(narrow ? 2 : 1); layout.setVgap(12);
                    middle.setPreferredSize(new Dimension(700, narrow ? 460 : 230));
                    middle.setMaximumSize(new Dimension(Integer.MAX_VALUE, narrow ? 480 : 245)); content.revalidate();
                }
            }
        });
        content.add(middle); content.add(Box.createVerticalStrut(12));
        JTable eventTable = DesktopTheme.table(groups), liveTable = DesktopTheme.table(logs), alertTable = DesktopTheme.table(alerts);
        activate(eventTable, () -> { int i = eventTable.getSelectedRow(); if (i >= 0 && snapshot != null) controller.details(snapshot, null, groups.rows.get(i).key(), this::showDetails); });
        activate(liveTable, () -> { int i = liveTable.getSelectedRow(); if (i >= 0) showLog(logs.rows.get(i), this); });
        activate(alertTable, () -> { int i = alertTable.getSelectedRow(); if (i >= 0) showText("Chi tiết cảnh báo", alerts.rows.get(i).toString(), this); });
        groupQuery.setName("event-query"); logQuery.setName("log-query");
        groupQuery.putClientProperty("JTextField.placeholderText", "Tìm IP hoặc nội dung sự kiện…");
        logQuery.putClientProperty("JTextField.placeholderText", "Tìm IP hoặc nội dung log…");
        eventsPanel.add(filterBar(groupLevel, groupQuery, order), BorderLayout.NORTH);
        eventsPanel.add(DesktopTheme.card("Các nhóm sự kiện", "10.000 log gần nhất • chọn nhóm để xem chi tiết", new JScrollPane(eventTable)));
        eventsPanel.add(groupCount, BorderLayout.SOUTH);
        logsPanel.add(filterBar(logLevel, logQuery, null), BorderLayout.NORTH);
        logsPanel.add(DesktopTheme.card("Log UI đã nhận", "10.000 log gần nhất • mới nhận trước • chọn dòng để đọc đầy đủ", new JScrollPane(liveTable)));
        logsPanel.add(logCount, BorderLayout.SOUTH);
        alertsPanel.add(DesktopTheme.card("Cảnh báo và thông báo", "Tối đa 1.000 bản tin từ Server • không tự suy đoán tấn công", new JScrollPane(alertTable)));
        alertsPanel.add(alertCount, BorderLayout.SOUTH);
        for (JPanel page : new JPanel[]{logsPanel, eventsPanel, alertsPanel}) page.setBorder(BorderFactory.createEmptyBorder(4, 22, 12, 22));
        liveTable.getColumnModel().getColumn(4).setPreferredWidth(360);
        eventTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        alertTable.getColumnModel().getColumn(3).setPreferredWidth(400);
        JScrollPane scroll = new JScrollPane(content); scroll.getVerticalScrollBar().setUnitIncrement(24); scroll.setBorder(null); add(scroll);
        DocumentListener change = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filters(); } public void removeUpdate(DocumentEvent e) { filters(); } public void changedUpdate(DocumentEvent e) { filters(); }
        };
        groupQuery.getDocument().addDocumentListener(change); logQuery.getDocument().addDocumentListener(change);
        groupLevel.addActionListener(e -> filters()); logLevel.addActionListener(e -> filters()); order.addActionListener(e -> filters());
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
        long[] values = {snapshot.total(), snapshot.severity().getOrDefault("INFO", 0L), snapshot.severity().getOrDefault("WARNING", 0L), snapshot.severity().getOrDefault("ERROR", 0L), snapshot.current() == null ? 0 : snapshot.current(), snapshot.ipCount()};
        for (int i = 0; i < kpis.length; i++) kpis[i].setText(i == 4 && snapshot.current() == null ? "—" : String.format("%,d", values[i]));
        traffic.update(snapshot); severity.update(snapshot.severity());
        rates.setText(String.format("Hiện tại: %s  |  Trung bình: %,.2f  |  Đỉnh: %,d log/giây  |  Trục Y: 0–%,.0f", snapshot.current() == null ? "—" : snapshot.current(), snapshot.average(), snapshot.peak(), snapshot.axisMax()));
        ips.set(snapshot.ips()); groups.set(snapshot.groups()); logs.set(snapshot.logs()); alerts.set(snapshot.alerts());
        logCount.setText(logs.getRowCount() + " log phù hợp / " + snapshot.retained() + " log đang giữ • Bộ lọc không thay đổi KPI");
        groupCount.setText(groups.getRowCount() + " nhóm phù hợp • Số lần và chi tiết tính trên toàn bộ nhóm");
        alertCount.setText(alerts.getRowCount() + " cảnh báo / thông báo đang giữ");
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
    static void showText(String title, String text, Component parent) {
        JTextArea area = new JTextArea(text, 18, 75); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setCaretPosition(0);
        JOptionPane.showMessageDialog(parent, new JScrollPane(area), title, JOptionPane.PLAIN_MESSAGE);
    }
}
