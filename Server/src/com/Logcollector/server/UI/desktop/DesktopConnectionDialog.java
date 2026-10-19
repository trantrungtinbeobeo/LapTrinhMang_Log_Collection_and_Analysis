package com.Logcollector.server.UI.desktop;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.text.ParseException;
import java.util.function.BiConsumer;

/** Draft values only; Cancel and invalid input have no effect on the running session. */
final class DesktopConnectionDialog extends JDialog {
    record Configuration(boolean demo, String address, String tcpHost, int tcpPort) {
        Configuration validate() {
            if (tcpHost == null || tcpHost.isBlank() || tcpHost.trim().matches(".*\\s+.*") || tcpPort < 1 || tcpPort > 65535)
                throw new IllegalArgumentException("Nhập hostname/IP TCP và cổng từ 1 đến 65535.");
            if (!demo) {
                URI uri;
                try { uri = URI.create(address.trim()); }
                catch (RuntimeException e) { throw new IllegalArgumentException("WebSocket không hợp lệ. Ví dụ: ws://localhost:8085"); }
                if (!("ws".equals(uri.getScheme()) || "wss".equals(uri.getScheme())) || uri.getHost() == null
                        || uri.getRawFragment() != null || uri.getRawUserInfo() != null || uri.getPort() == 0 || uri.getPort() > 65535)
                    throw new IllegalArgumentException("Nhập ws://host:port hoặc wss://host:port, không kèm tài khoản hoặc #fragment.");
            }
            return new Configuration(demo, address.trim(), tcpHost.trim(), tcpPort);
        }
    }
    DesktopConnectionDialog(Window owner, Configuration current, BiConsumer<Configuration, Boolean> apply) {
        super(owner, "Kết nối và nguồn dữ liệu", Dialog.ModalityType.MODELESS);
        setName("desktop-connection-dialog"); setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JComboBox<String> source = new JComboBox<>(new String[]{"Server thực tế", "Demo cục bộ"}); source.setName("connection-source");
        source.setSelectedIndex(current.demo() ? 1 : 0);
        JTextField address = new JTextField(current.address(), 27); address.setName("connection-address"); address.setEnabled(!current.demo());
        JTextField host = new JTextField(current.tcpHost(), 22); host.setName("connection-tcp-host");
        JSpinner port = new JSpinner(new SpinnerNumberModel(current.tcpPort(), 1, 65535, 1)); port.setName("connection-tcp-port");
        port.setEditor(new JSpinner.NumberEditor(port, "0"));
        source.addActionListener(e -> address.setEnabled(source.getSelectedIndex() == 0));
        JPanel input = new JPanel(new GridBagLayout()); input.setOpaque(false);
        addField(input, 0, "Nguồn dữ liệu", source); addField(input, 1, "WebSocket", address);
        addField(input, 2, "Đích gửi TCP", host); addField(input, 3, "Cổng TCP", port);
        JTextArea help = new JTextArea("Áp dụng / phiên mới: đặt lại thống kê UI và kết nối nguồn đã chọn.\n\nLưu đích TCP: chỉ đổi nơi gửi log test, giữ nguyên nguồn và phiên dữ liệu.\n\nDemo chạy cục bộ. Kết nối WebSocket không xác nhận TCP/UDP đang hoạt động.");
        help.setEditable(false); help.setLineWrap(true); help.setWrapStyleWord(true); help.setOpaque(false); help.setFocusable(false);
        DesktopTheme.foreground(help, "muted");
        JLabel error = new JLabel(" "); error.setName("connection-error"); DesktopTheme.foreground(error, "ERROR");
        JButton cancel = DesktopTheme.button("Hủy"), tcp = DesktopTheme.button("Lưu đích TCP"), start = DesktopTheme.button("Áp dụng / phiên mới");
        cancel.addActionListener(e -> dispose());
        java.util.function.Consumer<Boolean> save = newSession -> {
            try {
                port.commitEdit();
                Configuration next = new Configuration(newSession ? source.getSelectedIndex() == 1 : current.demo(),
                        newSession ? address.getText() : current.address(), host.getText(), ((Number) port.getValue()).intValue()).validate();
                apply.accept(next, newSession); dispose();
            } catch (ParseException | IllegalArgumentException invalid) {
                error.setText(invalid instanceof ParseException ? "Cổng TCP phải là số từ 1 đến 65535." : invalid.getMessage());
                error.setToolTipText(error.getText());
            }
        };
        tcp.addActionListener(e -> save.accept(false)); start.addActionListener(e -> save.accept(true));
        JPanel body = new JPanel(new BorderLayout(0, 16)); body.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        body.add(input, BorderLayout.NORTH); body.add(help);
        JPanel bottom = new JPanel(new BorderLayout(0, 10)); bottom.add(error, BorderLayout.NORTH); bottom.add(DesktopTheme.row(cancel, tcp, start), BorderLayout.SOUTH);
        body.add(bottom, BorderLayout.SOUTH); setContentPane(body);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(640, 425); setMinimumSize(new Dimension(600, 425)); setLocationRelativeTo(owner);
    }
    private static void addField(JPanel panel, int row, String label, JComponent input) {
        GridBagConstraints c = new GridBagConstraints(); c.gridy = row; c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 0, 12, 16);
        JLabel caption = new JLabel(label); caption.setLabelFor(input); panel.add(caption, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(0, 0, 12, 0); panel.add(input, c);
    }
}
