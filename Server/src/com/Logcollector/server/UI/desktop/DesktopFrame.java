package com.Logcollector.server.UI.desktop;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Screens stay alive across navigation; filters, cart and session are not recreated. */
public final class DesktopFrame extends JPanel implements AutoCloseable {
    /** Entry boundary for the legacy launcher; all implementation types remain package-private. */
    public static DesktopFrame create() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Create Desktop UI on EDT");
        DesktopTheme.install();
        return new DesktopFrame();
    }
    enum Page {
        OVERVIEW("Tổng quan", "Theo dõi lưu lượng và mức độ của log trong phiên", "overview"),
        LOGS("Nhật ký trực tiếp", "Tra cứu và đọc nội dung đầy đủ của từng log", "logs"),
        EVENTS("Thống kê sự kiện", "So sánh tần suất, Severity và các IP liên quan", "events"),
        ALERTS("Cảnh báo", "Cảnh báo và thông báo do Server phát", "alerts"),
        PRODUCTS("Sản phẩm test", "Thao tác sản phẩm và giỏ hàng để sinh log kiểm thử", "products"),
        LOGIN("Đăng nhập demo", "Kiểm thử đăng nhập thành công, thất bại và phiên khách", "login");
        final String title, description, id;
        Page(String title, String description, String id) { this.title = title; this.description = description; this.id = id; }
    }
    final DesktopController controller;
    final DesktopMonitor monitor;
    private final DesktopTestTools testTools;
    private final CardLayout pages = new CardLayout();
    private final JPanel deck = new JPanel(pages);
    private final Map<Page, JComponent> screens = new LinkedHashMap<>();
    private final Map<Page, JToggleButton> navigation = new LinkedHashMap<>();
    private final JLabel title = DesktopTheme.title("Tổng quan", 25), description = DesktopTheme.muted("");
    private final JLabel mode = DesktopTheme.title("SERVER • Chờ kết nối", 12);
    private final JLabel connection = DesktopTheme.muted("Chờ nguồn dữ liệu"), completeness = DesktopTheme.muted("Log UI nhận được từ lúc kết nối");
    private DesktopConnectionDialog.Configuration configuration = new DesktopConnectionDialog.Configuration(false, "ws://localhost:8085", "localhost", 9999);
    private DesktopConnectionDialog connectionDialog;
    private Page selected = Page.OVERVIEW;

    DesktopFrame() {
        super(new BorderLayout());
        controller = new DesktopController(this::monitorUpdate);
        monitor = new DesktopMonitor(controller);
        testTools = new DesktopTestTools(controller);
        screens.put(Page.OVERVIEW, monitor);
        screens.put(Page.LOGS, monitor.logsPanel);
        screens.put(Page.EVENTS, monitor.eventsPanel);
        screens.put(Page.ALERTS, monitor.alertsPanel);
        screens.put(Page.PRODUCTS, testTools.productsPanel());
        screens.put(Page.LOGIN, testTools.loginPanel());
        screens.forEach((page, component) -> { component.setName("screen-" + page.id); deck.add(component, page.id); });
        add(sidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        JPanel header = new JPanel(new BorderLayout(10, 0)); DesktopTheme.surface(header);
        header.setBorder(BorderFactory.createEmptyBorder(20, 22, 14, 22));
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 5)); heading.setOpaque(false);
        heading.add(title); heading.add(description); header.add(heading);
        JToggleButton theme = new JToggleButton("Nền tối"); theme.setName("desktop-theme-toggle");
        theme.setToolTipText("Đổi nền sáng / tối, giữ nguyên phiên dữ liệu");
        theme.addActionListener(e -> DesktopTheme.setDark(!DesktopTheme.isDark()));
        JButton settings = DesktopTheme.button("Kết nối"); settings.setName("desktop-connection");
        settings.setToolTipText("Chọn nguồn dữ liệu, địa chỉ WebSocket và đích gửi TCP");
        settings.addActionListener(e -> openConnection());
        header.add(DesktopTheme.row(settings, theme), BorderLayout.EAST);
        JPanel state = new JPanel(new GridLayout(0, 1, 0, 4)); state.setBorder(BorderFactory.createEmptyBorder(0, 22, 12, 22));
        state.add(connection); state.add(completeness);
        JPanel top = new JPanel(new BorderLayout()); top.add(header); top.add(state, BorderLayout.SOUTH);
        main.add(top, BorderLayout.NORTH); main.add(deck);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(8, 22, 10, 22)); footer.add(testTools.feedback());
        main.add(footer, BorderLayout.SOUTH); add(main);
        showPage(Page.OVERVIEW);
    }
    private JPanel sidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 18)); DesktopTheme.surface(sidebar);
        sidebar.setName("desktop-sidebar"); sidebar.setPreferredSize(new Dimension(204, 0));
        sidebar.setBorder(BorderFactory.createCompoundBorder(DesktopTheme.divider(false), BorderFactory.createEmptyBorder(24, 12, 18, 12)));
        JPanel brand = new JPanel(new GridLayout(0, 1, 0, 6)); brand.setOpaque(false);
        JLabel name = DesktopTheme.title("LOG COLLECTOR", 18); DesktopTheme.foreground(name, "blue");
        brand.add(name); brand.add(DesktopTheme.muted("Giám sát nhật ký mạng"));
        JPanel menu = new JPanel(); menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS)); menu.setOpaque(false);
        menu.add(DesktopTheme.muted("GIÁM SÁT")); menu.add(Box.createVerticalStrut(10));
        ButtonGroup buttons = new ButtonGroup();
        for (Page page : Page.values()) {
            if (page == Page.PRODUCTS) {
                menu.add(Box.createVerticalStrut(22)); menu.add(DesktopTheme.muted("CÔNG CỤ KIỂM THỬ")); menu.add(Box.createVerticalStrut(10));
            }
            JToggleButton item = new JToggleButton(page.title, new DesktopNavigationIcon(page.ordinal())) {
                protected void paintComponent(Graphics original) {
                    Graphics2D g = (Graphics2D) original.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (isSelected() || getModel().isRollover()) {
                        g.setColor(isSelected() ? DesktopTheme.SELECTION : DesktopTheme.STRIPE);
                        g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    }
                    if (hasFocus()) { g.setColor(DesktopTheme.BLUE); g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12); }
                    g.dispose(); super.paintComponent(original);
                }
            };
            item.setName("nav-" + page.id); item.setHorizontalAlignment(SwingConstants.LEFT); item.setIconTextGap(10);
            item.setMargin(new Insets(11, 10, 11, 8)); item.setAlignmentX(Component.LEFT_ALIGNMENT);
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44)); item.setPreferredSize(new Dimension(180, 44));
            item.setContentAreaFilled(false); item.setRolloverEnabled(true);
            item.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 8));
            item.getAccessibleContext().setAccessibleDescription(page.description);
            item.addActionListener(e -> showPage(page)); buttons.add(item); navigation.put(page, item);
            menu.add(item); menu.add(Box.createVerticalStrut(5));
        }
        JPanel upper = new JPanel(new BorderLayout(0, 30)); upper.setOpaque(false); upper.add(brand, BorderLayout.NORTH); upper.add(menu);
        sidebar.add(upper, BorderLayout.NORTH);
        JPanel bottom = new JPanel(new GridLayout(0, 1, 0, 8)); bottom.setOpaque(false);
        bottom.add(mode); bottom.add(DesktopTheme.muted("Java Desktop • Phiên cục bộ"));
        sidebar.add(bottom, BorderLayout.SOUTH);
        return sidebar;
    }
    void showPage(Page page) {
        selected = page; pages.show(deck, page.id); navigation.get(page).setSelected(true);
        title.setText(page.title); description.setText(page.description); description.setToolTipText(page.description);
    }
    Page selectedPage() { return selected; }
    JComponent screen(Page page) { return screens.get(page); }
    DesktopConnectionDialog.Configuration configuration() { return configuration; }
    void openConnection() {
        if (connectionDialog != null && connectionDialog.isDisplayable()) { connectionDialog.toFront(); return; }
        connectionDialog = new DesktopConnectionDialog(SwingUtilities.getWindowAncestor(this), configuration, (next, newSession) -> {
            controller.configureTcp(next.tcpHost(), next.tcpPort());
            if (newSession) controller.select(next.demo(), next.address());
            configuration = next;
            testTools.feedback().setText(newSession ? "Đã bắt đầu phiên mới; dữ liệu giám sát được đặt lại."
                    : "Đã lưu đích TCP: " + next.tcpHost() + ":" + next.tcpPort() + " • Giữ nguyên phiên dữ liệu");
        });
        connectionDialog.setVisible(true);
    }
    private void monitorUpdate(DesktopController.View view) {
        monitor.update(view);
        mode.setText(view.demo() ? "DEMO • DỮ LIỆU THỬ" : "SERVER • WebSocket");
        DesktopTheme.foreground(mode, view.demo() ? "WARNING" : "blue");
        connection.setText((view.demo() ? "" : configuration.address() + "  •  ") + view.status()
                + (!DesktopTheme.isFlatLaf() ? " • Nimbus dự phòng" : ""));
        connection.setToolTipText(connection.getText());
        completeness.setText((view.data().everDisconnected() ? "Thiếu dữ liệu kết nối  •  " : "Log UI nhận được từ lúc kết nối  •  ") + "Đang giữ: " + view.data().retained()
                + "  •  Bỏ quá tải: " + view.dropped() + "  •  Bản tin lỗi: " + view.malformed()
                );
        completeness.setToolTipText(completeness.getText() + " • Bắt đầu: "
                + java.time.Instant.ofEpochMilli(view.data().startedAt()).atZone(java.time.ZoneId.systemDefault()));
        DesktopTheme.foreground(completeness, view.dropped() + view.malformed() > 0 || view.data().everDisconnected() ? "WARNING" : "muted");
    }
    public void connect(boolean demo) {
        configuration = new DesktopConnectionDialog.Configuration(demo, configuration.address(), configuration.tcpHost(), configuration.tcpPort());
        controller.configureTcp(configuration.tcpHost(), configuration.tcpPort());
        controller.select(demo, configuration.address());
    }
    @Override
    public void close() { controller.close(); }
}
