package com.Logcollector.server.UI.desktop;

import javax.swing.*;
import java.awt.*;

/** Native vector icons, independent of font glyphs and image assets. */
final class DesktopNavigationIcon implements Icon {
    private final int kind;
    DesktopNavigationIcon(int kind) { this.kind = kind; }
    public int getIconWidth() { return 18; }
    public int getIconHeight() { return 18; }
    public void paintIcon(Component c, Graphics original, int x, int y) {
        Graphics2D g = (Graphics2D) original.create();
        g.translate(x, y); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c instanceof AbstractButton b && b.isSelected() ? DesktopTheme.BLUE : DesktopTheme.MUTED);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case 0 -> { for (int r = 0; r < 2; r++) for (int col = 0; col < 2; col++) g.drawRoundRect(2 + col * 8, 2 + r * 8, 5, 5, 1, 1); }
            case 1 -> { g.drawRoundRect(1, 2, 16, 14, 3, 3); g.drawLine(4, 6, 7, 9); g.drawLine(7, 9, 4, 12); g.drawLine(10, 12, 14, 12); }
            case 2 -> { g.drawLine(2, 16, 16, 16); g.drawRect(3, 10, 2, 6); g.drawRect(8, 3, 2, 13); g.drawRect(13, 7, 2, 9); }
            case 3 -> { g.drawPolygon(new int[]{9, 17, 1}, new int[]{1, 16, 16}, 3); g.drawLine(9, 6, 9, 10); g.fillOval(8, 12, 2, 2); }
            case 4 -> { g.drawRect(2, 5, 14, 11); g.drawLine(2, 5, 6, 1); g.drawLine(6, 1, 12, 1); g.drawLine(12, 1, 16, 5); g.drawLine(9, 5, 9, 10); }
            default -> { g.drawOval(6, 1, 6, 6); g.drawArc(2, 9, 14, 12, 0, 180); g.drawLine(2, 15, 16, 15); }
        }
        g.dispose();
    }
}
