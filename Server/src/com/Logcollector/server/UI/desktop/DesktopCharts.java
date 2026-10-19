package com.Logcollector.server.UI.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class DesktopCharts {
    static final class Traffic extends JPanel {
        private DesktopStatistics.Snapshot data;
        Traffic() { DesktopTheme.surface(this); setPreferredSize(new Dimension(700, 190)); }
        void update(DesktopStatistics.Snapshot snapshot) { data = snapshot; repaint(); }
        protected void paintComponent(Graphics original) {
            super.paintComponent(original);
            Graphics2D g = (Graphics2D) original.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int left = 72, top = 22, w = Math.max(1, getWidth() - left - 20), h = Math.max(1, getHeight() - top - 32);
                double max = data == null ? 100 : data.axisMax();
                g.setFont(getFont().deriveFont(11f));
                for (int i = 0; i <= 4; i++) {
                    int y = top + h - h * i / 4;
                    g.setColor(DesktopTheme.BORDER); g.drawLine(left, y, left + w, y);
                    g.setColor(DesktopTheme.MUTED); g.drawString(String.format(Locale.ROOT, "%,.0f", max * i / 4), 3, y + 4);
                }
                g.drawString("log/giây", 4, 12);
                if (data == null || data.rates().isEmpty()) { g.drawString("Chờ dữ liệu • cửa sổ 5 phút", left + 24, top + h / 2); return; }
                var rates = data.rates();
                long end = rates.get(rates.size() - 1).second(), start = end - 299;
                DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
                for (int i = 0; i <= 4; i++) {
                    long time = start + (299L * i / 4);
                    String label = format.format(Instant.ofEpochSecond(time));
                    int x = left + w * i / 4;
                    g.drawString(label, Math.max(left, Math.min(left + w - 52, x - 24)), top + h + 22);
                }
                Path2D line = new Path2D.Double(); boolean previous = false;
                for (var rate : rates) {
                    double x = left + (rate.second() - start) * w / 299.0;
                    if (!rate.available()) {
                        previous = false; g.setColor(DesktopTheme.GAP);
                        g.fillRect((int) x, top, Math.max(2, w / 300), h); continue;
                    }
                    double y = top + h - rate.count() * h / max;
                    if (previous) line.lineTo(x, y); else line.moveTo(x, y);
                    previous = true;
                    g.setColor(DesktopTheme.BLUE); g.fillOval((int) x - 2, (int) y - 2, 4, 4);
                }
                g.setStroke(new BasicStroke(2.2f)); g.setColor(DesktopTheme.BLUE); g.draw(line);
                if (data.everDisconnected()) { g.setColor(DesktopTheme.MUTED); g.drawString("Vùng vàng: thiếu dữ liệu kết nối", left + 12, top + 14); }
            } finally { g.dispose(); }
        }
    }
    static final class Severity extends JPanel {
        private Map<String, Long> counts = Map.of();
        Severity() { DesktopTheme.surface(this); setPreferredSize(new Dimension(280, 160)); }
        void update(Map<String, Long> counts) { this.counts = counts; repaint(); }
        protected void paintComponent(Graphics original) {
            super.paintComponent(original); Graphics2D g = (Graphics2D) original.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                long total = counts.values().stream().mapToLong(Long::longValue).sum();
                int diameter = Math.min(135, Math.max(60, getHeight() - 12)), y = (getHeight() - diameter) / 2;
                g.setColor(DesktopTheme.BORDER); g.fillOval(8, y, diameter, diameter);
                double angle = 90; int row = 0;
                for (var entry : new TreeMap<>(counts).entrySet()) {
                    double extent = total == 0 ? 0 : entry.getValue() * 360.0 / total;
                    g.setColor(DesktopTheme.severity(entry.getKey()));
                    g.fill(new java.awt.geom.Arc2D.Double(8, y, diameter, diameter, angle, -extent, java.awt.geom.Arc2D.PIE));
                    angle -= extent;
                    int labelY = 17 + row++ * 19;
                    g.fillRoundRect(diameter + 24, labelY - 9, 9, 9, 3, 3);
                    g.drawString(entry.getKey() + "  " + entry.getValue() + "  (" + String.format(Locale.ROOT, "%.1f", total == 0 ? 0 : entry.getValue() * 100.0 / total) + "%)", diameter + 40, labelY);
                }
                g.setColor(DesktopTheme.SURFACE); g.fillOval(8 + diameter / 4, y + diameter / 4, diameter / 2, diameter / 2);
                g.setColor(DesktopTheme.MUTED);
                String label = total == 0 ? "Chưa có log" : "Severity";
                g.drawString(label, Math.max(10, 8 + (diameter - g.getFontMetrics().stringWidth(label)) / 2), y + diameter / 2 + 4);
            } finally { g.dispose(); }
        }
    }
}
