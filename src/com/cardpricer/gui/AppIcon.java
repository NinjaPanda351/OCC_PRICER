package com.cardpricer.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** Resolution-independent line icons share the control's text color. */
public record AppIcon(Kind kind, int size) implements Icon {
    public enum Kind { HOME, CARDS, STACK, FILES, INVENTORY, SETTINGS, HELP, ARROW, SEARCH, SUN, MOON, PLUS, UNDO, MENU }
    public AppIcon(Kind kind) { this(kind, 19); }
    public int getIconWidth() { return size; }
    public int getIconHeight() { return size; }
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y); g.scale(size / 24d, size / 24d);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(component.getForeground()); g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case HOME -> { path(g, 3, 11, 12, 3, 21, 11); path(g, 5, 10, 5, 21, 10, 21, 10, 15, 14, 15, 14, 21, 19, 21, 19, 10); }
            case CARDS -> { g.rotate(-.22, 10, 12); g.drawRoundRect(3, 3, 13, 17, 3, 3); g.rotate(.22, 10, 12); g.drawRoundRect(9, 6, 12, 16, 3, 3); path(g, 12, 11, 18, 11); path(g, 12, 15, 16, 15); }
            case STACK -> { path(g, 3, 7, 12, 2, 21, 7, 12, 12, 3, 7); path(g, 3, 12, 12, 17, 21, 12); path(g, 3, 17, 12, 22, 21, 17); }
            case FILES -> { path(g, 3, 7, 3, 20, 21, 20, 21, 7, 12, 7, 10, 4, 3, 4, 3, 7); }
            case INVENTORY -> { g.drawRoundRect(4, 5, 16, 17, 2, 2); g.drawRoundRect(8, 2, 8, 5, 2, 2); path(g, 8, 12, 10, 14, 15, 10); path(g, 8, 18, 16, 18); }
            case SETTINGS -> { g.drawOval(5, 5, 14, 14); g.drawOval(9, 9, 6, 6); for (int i = 0; i < 8; i++) { g.rotate(Math.PI / 4, 12, 12); path(g, 12, 2, 12, 5); } }
            case HELP -> { g.drawOval(3, 3, 18, 18); g.draw(new Arc2D.Double(9, 7, 6, 6, 0, 180, Arc2D.OPEN)); path(g, 15, 10, 12, 13, 12, 14); g.fillOval(11, 17, 2, 2); }
            case ARROW -> { path(g, 4, 12, 20, 12); path(g, 14, 6, 20, 12, 14, 18); }
            case SEARCH -> { g.drawOval(3, 3, 12, 12); path(g, 13, 13, 21, 21); }
            case PLUS -> { path(g, 12, 5, 12, 19); path(g, 5, 12, 19, 12); }
            case MENU -> { path(g, 4, 6, 20, 6); path(g, 4, 12, 20, 12); path(g, 4, 18, 20, 18); }
            case UNDO -> { path(g, 8, 5, 3, 10, 8, 15); path(g, 3, 10, 15, 10); g.draw(new Arc2D.Double(10, 10, 10, 10, -90, 180, Arc2D.OPEN)); }
            case SUN -> { g.drawOval(8, 8, 8, 8); for (int i = 0; i < 8; i++) { g.rotate(Math.PI / 4, 12, 12); path(g, 12, 2, 12, 4); } }
            case MOON -> { g.draw(new Arc2D.Double(3, 3, 18, 18, 55, 280, Arc2D.OPEN)); g.draw(new Arc2D.Double(10, 1, 13, 15, 110, 220, Arc2D.OPEN)); }
        }
        g.dispose();
    }
    private static void path(Graphics2D g, double... points) {
        Path2D path = new Path2D.Double(); path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        g.draw(path);
    }
}
