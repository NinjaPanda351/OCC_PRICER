package com.cardpricer.util;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Centralised theme constants and FlatLaf-safe factory methods.
 *
 * <p>Surfaces and borders read the active look and feel at paint time. Primary
 * actions use a restrained blue fill through FlatLaf style properties.
 */
public final class AppTheme {

    // ── Semantic fallback colors ────────────────────────────────────────────
    public static final Color ACCENT  = new Color(0x3B82F6); // blue-500
    public static final Color DANGER  = new Color(0xDC2626); // red-600
    public static final Color SUCCESS = new Color(0x16A34A); // green-600

    // ── Fonts ─────────────────────────────────────────────────────────────────
    public static final Font FONT_TITLE   = new Font("Segoe UI", Font.BOLD,  24);
    public static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD,  14);
    public static final Font FONT_BODY    = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_SMALL   = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_MONO    = new Font("Consolas", Font.PLAIN, 12);

    // ── Spacing ───────────────────────────────────────────────────────────────
    public static final int GAP_SM = 8;
    public static final int GAP_MD = 14;
    public static final int GAP_LG = 20;

    private AppTheme() {}

    public static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key); return value == null ? fallback : value;
    }
    public static Color surface() { return color("OCC.surface", color("Panel.background", Color.DARK_GRAY)); }
    public static Color accent() { return color("OCC.accent", new Color(0x426AB0)); }
    public static Color muted() { return color("Label.disabledForeground", Color.GRAY); }
    public static Color border() { return color("Component.borderColor", new Color(0xD0D7E2)); }
    public static Color success() { return color("OCC.success", SUCCESS); }
    public static JLabel mutedLabel(String text) {
        return new JLabel(text) {
            @Override public void updateUI() { super.updateUI(); setForeground(muted()); setFont(FONT_SMALL); }
        };
    }
    public static JLabel eyebrow(String text) {
        return new JLabel(text) {
            @Override public void updateUI() {
                super.updateUI(); setForeground(muted()); setFont(FONT_SMALL.deriveFont(Font.BOLD, 10f));
            }
        };
    }
    public static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout); panel.setOpaque(false); return panel;
    }
    public static Border cardBorder(int padding) {
        return BorderFactory.createCompoundBorder(new RoundedBorder(14), new EmptyBorder(padding, padding, padding, padding));
    }
    public static JPanel surface(LayoutManager layout, int padding) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(surface()); g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14); g.dispose();
            }
        };
        panel.setOpaque(false); panel.setBorder(cardBorder(padding)); return panel;
    }
    public static void styleTable(JTable table) {
        table.setRowHeight(com.formdev.flatlaf.util.UIScale.scale(34)); table.setShowVerticalLines(false); table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0)); table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(FONT_SMALL.deriveFont(Font.BOLD));
        table.getTableHeader().setPreferredSize(new Dimension(0, com.formdev.flatlaf.util.UIScale.scale(34)));
    }
    private static final class RoundedBorder extends javax.swing.border.AbstractBorder {
        private final int arc;
        RoundedBorder(int arc) { this.arc = arc; }
        @Override public Insets getBorderInsets(Component c) { return new Insets(1, 1, 1, 1); }
        @Override public Insets getBorderInsets(Component c, Insets insets) { insets.set(1, 1, 1, 1); return insets; }
        @Override public void paintBorder(Component c, Graphics graphics, int x, int y, int w, int h) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(border()); g.drawRoundRect(x, y, w - 1, h - 1, arc, arc); g.dispose();
        }
    }

    // ── Button factories ──────────────────────────────────────────────────────

    /**
     * Primary action button with a blue fill and high-contrast white text.
     */
    public static JButton primaryButton(String label) {
        JButton b = secondaryButton(label);
        b.putClientProperty("FlatLaf.style", "background: #4266AE; foreground: #FFFFFF; borderColor: #4266AE; hoverBackground: #5078BF; hoverBorderColor: #5078BF; pressedBackground: #385995");
        b.setFont(FONT_BODY.deriveFont(Font.BOLD));
        return b;
    }

    /**
     * Destructive action with a quiet red foreground.
     */
    public static JButton dangerButton(String label) {
        JButton b = secondaryButton(label);
        b.putClientProperty("FlatLaf.style", "foreground: #D9687C");
        return b;
    }

    /**
     * Secondary/neutral button — outlined round-rect, no fill override.
     * The active FlatLaf theme owns the exact color.
     */
    public static JButton secondaryButton(String label) {
        JButton b = new JButton(label);
        b.setMargin(new Insets(7, 12, 7, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ── Border factory ────────────────────────────────────────────────────────

    /**
     * Standard section border: titled border with inner padding.
     * No hardcoded line color — the theme owns the border color.
     */
    public static Border sectionBorder(String title) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(new RoundedBorder(12), title,
                        javax.swing.border.TitledBorder.LEADING, javax.swing.border.TitledBorder.TOP, FONT_SMALL.deriveFont(Font.BOLD)),
                new EmptyBorder(10, 12, 12, 12)
        );
    }

    // ── Panel header factory ──────────────────────────────────────────────────

    /**
     * Consistent panel header with a large title and a muted subtitle.
     * No hardcoded foreground on the title — only the subtitle uses the
     * theme-adaptive {@code Label.disabledForeground} color.
     */
    public static JPanel panelHeader(String title, String subtitle) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(0, 0, 8, 0));

        JLabel t = new JLabel(title);
        t.setFont(FONT_TITLE);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel s = mutedLabel(subtitle);
        s.setFont(FONT_SMALL);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(t);
        p.add(Box.createVerticalStrut(4));
        p.add(s);
        return p;
    }

    // ── Global FlatLaf tweaks ─────────────────────────────────────────────────

    /**
     * Applies non-color FlatLaf UI defaults that improve visual polish
     * without overriding any theme's color palette.
     *
     * <p>Call after applying a look and feel, before creating or updating components.
     */
    public static void applyFlatLafTweaks() {
        UIManager.put("defaultFont", new javax.swing.plaf.FontUIResource(FONT_BODY));
        UIManager.put("ScrollBar.width",    8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("Table.rowHeight",    32);
        UIManager.put("Component.arc", 10); UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10); UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0); UIManager.put("Button.default.boldText", true);
        UIManager.put("Table.showHorizontalLines", false); UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.cellMargins", new Insets(0, 10, 0, 10));
        UIManager.put("TextField.margin", new Insets(6, 9, 6, 9));
        UIManager.put("ComboBox.padding", new Insets(6, 9, 6, 9));
        UIManager.put("TabbedPane.tabHeight", 42); UIManager.put("TabbedPane.tabInsets", new Insets(8, 18, 8, 18));
        UIManager.put("TabbedPane.showContentSeparator", false); UIManager.put("SplitPane.dividerSize", 8);
        // No color-based UIManager.put() calls — theme owns all palette colors
    }
}
