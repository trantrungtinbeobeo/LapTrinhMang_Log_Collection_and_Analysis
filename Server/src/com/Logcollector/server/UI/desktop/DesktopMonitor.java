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

    // Top IP panel — Stitch-style bars + hover tooltip
    private List<DesktopStatistics.Ip> displayedIps = List.of();
    private final JTextField ipSearch = new JTextField();
    private final IpBarPanel ipBarPanel = new IpBarPanel();

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
        long[] values = {snapshot.total(), snapshot.severity().getOrDefault("INFO", 0L), snapshot.severity().getOrDefault("WARNING", 0L), snapshot.severity().getOrDefault("ERROR", 0L), snapshot.current() == null ? 0 : snapshot.current(), snapshot.ipCount()};
        for (int i = 0; i < kpis.length; i++) kpis[i].setText(i == 4 && snapshot.current() == null ? "—" : String.format("%,d", values[i]));
        traffic.update(snapshot); severity.update(snapshot.severity());
        rates.setText(String.format("Hiện tại: %s  |  Trung bình: %,.2f  |  Đỉnh: %,d log/giây  |  Trục Y: 0–%,.0f", snapshot.current() == null ? "—" : snapshot.current(), snapshot.average(), snapshot.peak(), snapshot.axisMax()));
        // Re-filter IPs preserving search text
        filterIps();
        groups.set(snapshot.groups()); logs.set(snapshot.logs()); alerts.set(snapshot.alerts());
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
