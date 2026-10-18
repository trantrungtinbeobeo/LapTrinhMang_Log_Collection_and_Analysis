package com.Logcollector.server.UI.desktop;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/** Swing-only fixture tools. They never authenticate real users or perform a real purchase. */
final class DesktopTestTools {
    static final class Cart {
        private final Map<DesktopCatalog.Product, Integer> items = new LinkedHashMap<>();
        void add(DesktopCatalog.Product product) { quantity(product, items.getOrDefault(product, 0) + 1); }
        void quantity(DesktopCatalog.Product product, int amount) {
            if (amount < 0 || amount > 999) throw new IllegalArgumentException("Số lượng phải từ 0 đến 999");
            if (amount == 0) items.remove(product); else items.put(product, amount);
        }
        long total() { return items.entrySet().stream().mapToLong(e -> e.getKey().price() * e.getValue()).sum(); }
        int count() { return items.values().stream().mapToInt(Integer::intValue).sum(); }
        List<Map.Entry<DesktopCatalog.Product, Integer>> entries() { return items.entrySet().stream().map(e -> Map.entry(e.getKey(), e.getValue())).toList(); }
        boolean empty() { return items.isEmpty(); }
        void clear() { items.clear(); }
    }
    private final DesktopController controller;
    private final JLabel feedback = DesktopTheme.muted("Mọi thao tác ở đây là thử nghiệm; không phát sinh giao dịch thật.");
    private String user = "Khách demo";
    private final Cart cart = new Cart();
    DesktopTestTools(DesktopController controller) { this.controller = controller; }
    JLabel feedback() { return feedback; }
    private void emit(String action, String details, boolean warning) {
        feedback.setText("Đang xử lý log test " + action + "…");
        controller.emit(action, "User '" + user + "' " + details, warning, feedback::setText);
    }
    static boolean accepts(String email, char[] password) {
        if (email == null || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) return false;
        // Public demo fixture only, matching the existing web UI's quick-test passwords.
        String candidate = new String(password).trim();
        return Set.of("Demo123!", "123456", "admin", "admin123").contains(candidate);
    }
    JPanel loginPanel() {
        JPanel form = new JPanel(new GridBagLayout()); DesktopTheme.surface(form);
        JTextField email = new JTextField("minhan@demostore.vn", 25);
        JPasswordField password = new JPasswordField(25);
        JLabel session = DesktopTheme.title("Phiên: Khách demo", 17);
        JButton login = DesktopTheme.button("Đăng nhập demo"), guest = DesktopTheme.button("Vào bằng khách"), logout = DesktopTheme.button("Đăng xuất");
        GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(9, 12, 9, 12);
        form.add(session, c); c.gridy++; form.add(new JLabel("Email demo"), c); c.gridx = 1; form.add(email, c);
        c.gridx = 0; c.gridy++; form.add(new JLabel("Mật khẩu demo"), c); c.gridx = 1; form.add(password, c);
        c.gridx = 0; c.gridy++; c.gridwidth = 2; form.add(DesktopTheme.row(login, guest, logout), c);
        c.gridy++; form.add(DesktopTheme.muted("Mật khẩu test: Demo123! / 123456 / admin / admin123"), c);
        c.gridy++; form.add(DesktopTheme.muted("Đây không phải cơ chế bảo vệ Dashboard hoặc tài khoản thật."), c);
        login.addActionListener(e -> {
            char[] secret = password.getPassword(); boolean ok;
            try { ok = accepts(email.getText(), secret); } finally { Arrays.fill(secret, '\0'); password.setText(""); }
            if (ok) { user = email.getText().trim(); session.setText("Phiên demo: " + user); }
            emit(ok ? "LOGIN_SUCCESS" : "LOGIN_FAILED", "attempted email '" + email.getText().trim() + "' " + (ok ? "logged in successfully" : "failed password"), !ok);
            if (!ok) session.setText("Đăng nhập thất bại • phiên hiện tại: " + user);
        });
        password.addActionListener(e -> login.doClick());
        guest.addActionListener(e -> { user = "Khách demo"; password.setText(""); session.setText("Phiên: Khách demo"); emit("GUEST_LOGIN", "entered guest session", false); });
        logout.addActionListener(e -> { emit("LOGOUT", "logged out", false); user = "Khách demo"; session.setText("Phiên: Khách demo"); password.setText(""); });
        JPanel content = new DesktopTheme.ScrollContent(); content.setLayout(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(24, 22, 24, 22));
        GridBagConstraints centered = new GridBagConstraints(); centered.gridx = 0; centered.gridy = 0; centered.weightx = 1; centered.anchor = GridBagConstraints.NORTH;
        content.add(DesktopTheme.card("Phiên đăng nhập thử nghiệm", "Nhập email bất kỳ đúng định dạng và mật khẩu demo", form), centered);
        centered.gridy++; centered.weighty = 1; content.add(Box.createVerticalGlue(), centered);
        JPanel panel = new JPanel(new BorderLayout()); JScrollPane scroll = new JScrollPane(content); scroll.setBorder(null); panel.add(scroll); return panel;
    }
    JPanel productsPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12)); panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        JTextField search = new JTextField(28); JComboBox<String> category = new JComboBox<>(new String[]{"Tất cả", "Laptop", "Điện thoại", "Phụ kiện"});
        var model = new DesktopTheme.Rows<DesktopCatalog.Product>(new String[]{"Mã", "Sản phẩm", "Danh mục", "Giá demo"},
                (p, column) -> switch (column) { case 0 -> p.id(); case 1 -> p.name(); case 2 -> p.category(); default -> money(p.price()); });
        model.set(DesktopCatalog.PRODUCTS); JTable table = DesktopTheme.table(model);
        table.getColumnModel().getColumn(0).setMinWidth(105);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(1).setMinWidth(160);
        table.getColumnModel().getColumn(2).setMinWidth(85);
        table.getColumnModel().getColumn(3).setMinWidth(120);
        Runnable filter = () -> {
            String q = search.getText().trim().toLowerCase(Locale.ROOT); String selected = (String) category.getSelectedItem();
            model.set(DesktopCatalog.PRODUCTS.stream().filter(p -> selected.equals("Tất cả") || p.category().equals(selected))
                    .filter(p -> (p.id() + " " + p.name() + " " + p.category()).toLowerCase(Locale.ROOT).contains(q)).toList());
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter.run(); } public void removeUpdate(DocumentEvent e) { filter.run(); } public void changedUpdate(DocumentEvent e) { filter.run(); }
        }); category.addActionListener(e -> filter.run());
        JLabel total = DesktopTheme.title("Giỏ hàng: 0 món • 0 ₫", 17);
        var cartModel = new DesktopTheme.Rows<Map.Entry<DesktopCatalog.Product, Integer>>(new String[]{"Sản phẩm", "Số lượng", "Thành tiền"},
                (item, column) -> column == 0 ? item.getKey().name() : column == 1 ? item.getValue() : money(item.getKey().price() * item.getValue()));
        JTable cartTable = DesktopTheme.table(cartModel);
        cartTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        cartTable.getColumnModel().getColumn(1).setMinWidth(70);
        cartTable.getColumnModel().getColumn(2).setMinWidth(120);
        Runnable refresh = () -> { cartModel.set(cart.entries()); total.setText("Giỏ hàng: " + cart.count() + " món • " + money(cart.total())); };
        JButton detail = DesktopTheme.button("Xem chi tiết"), add = DesktopTheme.button("Thêm vào giỏ"), buy = DesktopTheme.button("Thanh toán ngay (demo)");
        Runnable showDetail = () -> {
            int i = table.getSelectedRow(); if (i < 0) return;
            var product = model.rows.get(i); emit("VIEW_PRODUCT", "viewed '" + product.id() + "' " + product.name(), false);
            DesktopMonitor.showText(product.name(), product.id() + "\n" + product.category() + "\n" + money(product.price()) + "\n\n" + product.description(), panel);
        };
        detail.addActionListener(e -> showDetail.run());
        table.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "viewProduct");
        table.getActionMap().put("viewProduct", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { showDetail.run(); } });
        table.addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) showDetail.run(); } });
        add.addActionListener(e -> {
            int i = table.getSelectedRow(); if (i < 0) return; var p = model.rows.get(i);
            try { cart.add(p); refresh.run(); emit("ADD_TO_CART", "added '" + p.id() + "'", false); }
            catch (IllegalArgumentException error) { feedback.setText(error.getMessage()); }
        });
        buy.addActionListener(e -> { int i = table.getSelectedRow(); if (i < 0) return; var p = model.rows.get(i); emit("CHECKOUT_ORDER", "simulated checkout '" + p.id() + "', quantity=1", false); });
        JPanel catalog = new JPanel(new BorderLayout(8, 8)); catalog.setOpaque(false);
        search.putClientProperty("JTextField.placeholderText", "Tìm mã hoặc tên sản phẩm…");
        JPanel searchBar = new JPanel(new BorderLayout(8, 0)); searchBar.setOpaque(false); searchBar.add(search); searchBar.add(category, BorderLayout.EAST);
        catalog.add(searchBar, BorderLayout.NORTH);
        catalog.add(new JScrollPane(table)); catalog.add(actions(detail, add, buy), BorderLayout.SOUTH);
        JPanel cartPanel = new JPanel(new BorderLayout(8, 8)); cartPanel.setOpaque(false); cartPanel.add(total, BorderLayout.NORTH); cartPanel.add(new JScrollPane(cartTable));
        JButton quantity = DesktopTheme.button("Sửa số lượng"), remove = DesktopTheme.button("Xóa sản phẩm"), clear = DesktopTheme.button("Xóa giỏ"), checkout = DesktopTheme.button("Thanh toán giỏ (demo)");
        quantity.addActionListener(e -> {
            int i = cartTable.getSelectedRow(); if (i < 0) return; var item = cartModel.rows.get(i);
            JSpinner amount = new JSpinner(new SpinnerNumberModel(item.getValue().intValue(), 0, 999, 1));
            if (JOptionPane.showConfirmDialog(panel, amount, "Số lượng (0 để xóa)", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            int value = (Integer) amount.getValue(); if (value == item.getValue()) return;
            cart.quantity(item.getKey(), value); refresh.run(); emit("CART_UPDATE", "product '" + item.getKey().id() + "', quantity=" + value, false);
        });
        remove.addActionListener(e -> { int i = cartTable.getSelectedRow(); if (i < 0) return; var p = cartModel.rows.get(i).getKey(); cart.quantity(p, 0); refresh.run(); emit("REMOVE_FROM_CART", "removed '" + p.id() + "'", false); });
        clear.addActionListener(e -> { if (cart.empty()) return; cart.clear(); refresh.run(); emit("CLEAR_CART", "cleared cart", false); });
        checkout.addActionListener(e -> {
            if (cart.empty()) return;
            String details = "simulated checkout " + cart.count() + " items, demo total=" + cart.total();
            cart.clear(); refresh.run(); emit("CHECKOUT_ORDER", details, false);
        });
        cartPanel.add(actions(quantity, remove, clear, checkout), BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                DesktopTheme.card("Danh mục sản phẩm test", "8 sản phẩm mẫu • không có giao dịch thật", catalog),
                DesktopTheme.card("Giỏ hàng demo", "Giỏ chỉ lưu trong phiên Desktop", cartPanel));
        split.getLeftComponent().setMinimumSize(new Dimension(520, 230)); split.getRightComponent().setMinimumSize(new Dimension(350, 240));
        split.setResizeWeight(0.62); split.setDividerLocation(330); split.setBorder(null);
        JPanel content = new DesktopTheme.ScrollContent(true); content.setLayout(new BorderLayout()); content.add(split);
        content.setPreferredSize(new Dimension(800, 650));
        panel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent e) {
                int orientation = panel.getWidth() >= 1050 ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT;
                if (split.getOrientation() != orientation) {
                    split.setOrientation(orientation);
                    content.setPreferredSize(new Dimension(800, orientation == JSplitPane.HORIZONTAL_SPLIT ? 480 : 650));
                    content.revalidate(); split.setDividerLocation(0.62);
                }
            }
        });
        // At smaller heights, the page scrolls instead of hiding cart controls below the footer.
        JScrollPane scroll = new JScrollPane(content); scroll.setBorder(null); scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll); return panel;
    }
    private static JPanel actions(JButton... buttons) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8)); panel.setOpaque(false);
        for (JButton button : buttons) panel.add(button);
        return panel;
    }
    static String money(long value) { return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN")).format(value); }
}
