package com.cardpricer.gui;

import com.cardpricer.util.AppTheme;
import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;

/** Keeps table headers and keyboard behavior while explaining an empty workspace. */
public final class EmptyStateTable extends JTable {
    private final String title, detail;
    public EmptyStateTable(TableModel model, String title, String detail) {
        super(model); this.title = title; this.detail = detail;
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (getRowCount() != 0 || title == null || getHeight() < 110) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle visible = getVisibleRect();
        int center = visible.x + visible.width / 2, y = Math.max(35, getHeight() / 2 - 28);
        JLabel symbol = new JLabel(); symbol.setForeground(AppTheme.accent());
        new AppIcon(AppIcon.Kind.CARDS, 28).paintIcon(symbol, g, center - 14, y - 26);
        g.setFont(AppTheme.FONT_HEADING); g.setColor(getForeground());
        g.drawString(title, center - g.getFontMetrics().stringWidth(title) / 2, y + 24);
        g.setFont(AppTheme.FONT_SMALL); g.setColor(AppTheme.muted());
        g.drawString(detail, center - g.getFontMetrics().stringWidth(detail) / 2, y + 48);
        g.dispose();
    }
}
