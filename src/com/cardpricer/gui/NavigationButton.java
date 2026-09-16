package com.cardpricer.gui;

import com.cardpricer.util.AppTheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Quiet navigation with an explicit selection and keyboard focus indicator. */
final class NavigationButton extends JToggleButton {
    NavigationButton(String text, Icon icon) {
        super(text, icon);
        setContentAreaFilled(false); setBorder(new EmptyBorder(12, 14, 12, 14));
        setFocusPainted(false); setRolloverEnabled(true);
    }
    @Override public Color getForeground() {
        return getModel() != null && isSelected() ? AppTheme.accent() : AppTheme.color("Label.foreground", Color.DARK_GRAY);
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (isSelected() || getModel().isRollover()) {
            g.setColor(isSelected() ? AppTheme.color("OCC.accentSurface", new Color(0xE1E9F7)) : AppTheme.surface());
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        }
        if (hasFocus()) {
            g.setColor(AppTheme.accent()); g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);
        }
        g.dispose(); super.paintComponent(graphics);
    }
}
