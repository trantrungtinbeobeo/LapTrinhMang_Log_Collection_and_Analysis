package com.Logcollector.server.UI.desktop;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.function.BiFunction;

final class DesktopTheme {
    static Color CANVAS, SURFACE, INK, MUTED, BLUE, BORDER, STRIPE, SELECTION, GAP;
    private static boolean dark, flatLaf;
    static void install() {
        setPalette(false);
        installLookAndFeel();
    }
    static boolean isDark() { return dark; }
    static boolean isFlatLaf() { return flatLaf; }
    private static void setPalette(boolean value) {
        dark = value;
        CANVAS = new Color(value ? 0x111418 : 0xffffff);
        SURFACE = new Color(value ? 0x1d2024 : 0xffffff);
        INK = new Color(value ? 0xe1e2e8 : 0x131b2e);
        MUTED = new Color(value ? 0xb1b9c6 : 0x586074);
        BLUE = new Color(value ? 0x8ab4ff : 0x2563eb);
        BORDER = new Color(value ? 0x343b46 : 0xe2e7ef);
        STRIPE = new Color(value ? 0x22272e : 0xf8fafc);
        SELECTION = new Color(value ? 0x263e63 : 0xe3edff);
        GAP = new Color(value ? 0x493b22 : 0xfff3ce);
    }
    private static void installLookAndFeel() {
        try {
            String name = dark ? "com.formdev.flatlaf.FlatDarkLaf" : "com.formdev.flatlaf.FlatLightLaf";
            UIManager.setLookAndFeel((LookAndFeel) Class.forName(name).getDeclaredConstructor().newInstance());
            flatLaf = true;
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException | LinkageError error) {
            flatLaf = false;
            System.err.println("[Desktop UI] FlatLaf unavailable; using Nimbus. Run UI/run-desktop.ps1 with UI/lib/flatlaf-3.7.jar.");
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
                    if (info.getName().equals("Nimbus")) { UIManager.setLookAndFeel(info.getClassName()); break; }
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) { }
        }
        Font font = new javax.swing.plaf.FontUIResource("Segoe UI", Font.PLAIN, 13);
        for (Object key : UIManager.getDefaults().keySet().toArray()) if (key.toString().endsWith(".font")) UIManager.put(key, font);
        UIManager.put("defaultFont", font);
        UIManager.put("Panel.background", CANVAS);
        UIManager.put("Viewport.background", CANVAS);
        UIManager.put("control", CANVAS);
        UIManager.put("nimbusBase", BLUE);
        UIManager.put("nimbusBlueGrey", BORDER);
        UIManager.put("nimbusOrange", BLUE);
        UIManager.put("Label.foreground", INK);
        UIManager.put("TabbedPane.selected", SURFACE);
        UIManager.put("TabbedPane.background", CANVAS);
        UIManager.put("TabbedPane.foreground", INK);
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", INK);
        UIManager.put("TableHeader.background", STRIPE);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("Table.selectionBackground", SELECTION);
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextField.foreground", INK);
        UIManager.put("PasswordField.background", SURFACE);
        UIManager.put("PasswordField.foreground", INK);
        UIManager.put("TextArea.background", SURFACE);
        UIManager.put("TextArea.foreground", INK);
        UIManager.put("ToolTip.background", SURFACE);
        UIManager.put("ToolTip.foreground", INK);
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("ToggleButton.selectedBackground", SELECTION);
        UIManager.put("ToggleButton.selectedForeground", BLUE);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("ProgressBar.foreground", BLUE);
        UIManager.put("ProgressBar.background", STRIPE);
        UIManager.put("Component.focusColor", BLUE);
        UIManager.put("ScrollBar.width", 12);
        // UIResource lets updateComponentTreeUI replace defaults on existing components.
        // Explicit semantic colors on our panels/labels are reapplied separately below.
        for (Object key : UIManager.getDefaults().keySet().toArray()) {
            Object color = UIManager.get(key);
            if (color instanceof Color value && !(color instanceof javax.swing.plaf.UIResource))
                UIManager.put(key, new javax.swing.plaf.ColorUIResource(value));
        }
    }
    static void setDark(boolean value) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme changes must run on EDT");
        if (dark == value) return;
        setPalette(value);
        installLookAndFeel();
        for (Window window : Window.getWindows()) if (window.isDisplayable()) {
            SwingUtilities.updateComponentTreeUI(window);
            applyRoles(window);
            window.validate(); window.repaint();
        }
    }
    static void surface(JComponent component) {
        component.putClientProperty("desktop.background", "surface"); component.setBackground(SURFACE);
    }
    static void foreground(JComponent component, String role) {
        component.putClientProperty("desktop.foreground", role);
        component.setForeground(switch (role) {
            case "muted" -> MUTED; case "blue" -> BLUE; case "ink" -> INK;
            default -> severity(role);
        });
    }
    private static void applyRoles(Component component) {
        if (component instanceof JComponent c) {
            if (c instanceof JToggleButton toggle && "desktop-theme-toggle".equals(c.getName())) {
                toggle.setSelected(dark); toggle.setText(dark ? "Nền sáng" : "Nền tối");
            }
            if ("surface".equals(c.getClientProperty("desktop.background"))) c.setBackground(SURFACE);
            Object role = c.getClientProperty("desktop.foreground");
            if (role instanceof String name) foreground(c, name);
        }
        if (component instanceof Container container) for (Component child : container.getComponents()) applyRoles(child);
    }
    static JLabel title(String text, int size) {
        JLabel label = new JLabel(text); label.setFont(label.getFont().deriveFont(Font.BOLD, size)); return label;
    }
    static Border divider(boolean horizontal) {
        return new AbstractBorder() {
            public Insets getBorderInsets(Component c) { return horizontal ? new Insets(0, 0, 1, 0) : new Insets(0, 0, 0, 1); }
            public Insets getBorderInsets(Component c, Insets i) { Insets value = getBorderInsets(c); i.set(value.top, value.left, value.bottom, value.right); return i; }
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                g.setColor(BORDER);
                if (horizontal) g.drawLine(x, y + h - 1, x + w, y + h - 1); else g.drawLine(x + w - 1, y, x + w - 1, y + h);
            }
        };
    }
    static JLabel muted(String text) { JLabel label = new JLabel(text); foreground(label, "muted"); return label; }
    static JPanel card(String title, String subtitle, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout(8, 8)); surface(panel);
        panel.setBorder(new CompoundBorder(new AbstractBorder() {
            public Insets getBorderInsets(Component c) { return new Insets(1, 1, 1, 1); }
            public Insets getBorderInsets(Component c, Insets i) { i.set(1, 1, 1, 1); return i; }
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                Graphics2D copy = (Graphics2D) g.create();
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                copy.setColor(BORDER); copy.drawRoundRect(x, y, width - 1, height - 1, 12, 12); copy.dispose();
            }
        }, new EmptyBorder(12, 14, 12, 14)));
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 3)); heading.setOpaque(false);
        heading.add(title(title, 15)); if (subtitle != null && !subtitle.isEmpty()) heading.add(muted(subtitle));
        panel.add(heading, BorderLayout.NORTH); panel.add(body, BorderLayout.CENTER); return panel;
    }
    static JPanel row(JComponent... elements) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5)); panel.setOpaque(false);
        for (JComponent c : elements) panel.add(c); return panel;
    }
    static JButton button(String text) { JButton b = new JButton(text); b.setFocusPainted(true); return b; }
    static Color severity(String level) {
        return switch (level) {
            case "INFO" -> new Color(dark ? 0x73d99e : 0x006329); case "WARNING" -> new Color(dark ? 0xffbc70 : 0xb45309);
            case "ERROR" -> new Color(dark ? 0xff9292 : 0xba1a1a); case "CRITICAL", "EMERGENCY", "ALERT" -> new Color(dark ? 0xffa7ca : 0x7f1d1d);
            case "DEBUG" -> new Color(dark ? 0xb4bdcc : 0x64748b); default -> BLUE;
        };
    }
    static <T> JTable table(Rows<T> model) {
        JTable table = new JTable(model) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getRowCount() == 0) {
                    g.setColor(MUTED); String label = "Chưa có dữ liệu phù hợp";
                    g.drawString(label, Math.max(12, (getWidth() - g.getFontMetrics().stringWidth(label)) / 2), 40);
                }
            }
        };
        model.table = table;
        table.setRowHeight(30); table.setFillsViewportHeight(true); table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0)); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false); table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean selected, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, v, selected, focus, r, c);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                setBackground(selected ? SELECTION : r % 2 == 0 ? SURFACE : STRIPE); setForeground(INK);
                if (t.getColumnName(c).equals("Severity")) setForeground(severity(String.valueOf(v)));
                setToolTipText(String.valueOf(v)); return this;
            }
        });
        return table;
    }
    static final class Rows<T> extends AbstractTableModel {
        final String[] names;
        final BiFunction<T, Integer, Object> value;
        List<T> rows = List.of();
        JTable table;
        java.util.function.Function<T, Object> key = value -> value;
        Rows(String[] names, BiFunction<T, Integer, Object> value) { this.names = names; this.value = value; }
        void set(List<T> rows) {
            if (this.rows.equals(rows)) return;
            int selected = table == null ? -1 : table.getSelectedRow();
            Object selectedKey = selected >= 0 && selected < this.rows.size() ? key.apply(this.rows.get(selected)) : null;
            this.rows = rows; fireTableDataChanged();
            if (selectedKey != null) for (int i = 0; i < rows.size(); i++) {
                if (selectedKey.equals(key.apply(rows.get(i)))) { table.setRowSelectionInterval(i, i); break; }
            }
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return names.length; }
        public String getColumnName(int column) { return names[column]; }
        public Object getValueAt(int row, int column) { return value.apply(rows.get(row), column); }
    }
    static final class ScrollContent extends JPanel implements Scrollable {
        private final boolean fillHeight;
        ScrollContent() { this(false); }
        ScrollContent(boolean fillHeight) { this.fillHeight = fillHeight; }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(24, visible.height - 48); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return fillHeight && getParent() != null && getParent().getHeight() >= getPreferredSize().height; }
    }
}
