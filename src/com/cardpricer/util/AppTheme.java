package com.cardpricer.util;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * Centralised theme constants and FlatLaf-safe factory methods.
 *
 * <p>Colors are used <em>only</em> for {@code FlatLaf.style} client properties
 * and {@link MatteBorder} — never as panel backgrounds or default label foregrounds,
 * so all 32 registered themes continue to work correctly.
 */
public final class AppTheme {

    // ── Colors (ONLY for FlatLaf.style and MatteBorder) ───────────────────────
    public static final Color ACCENT  = new Color(0x3B82F6); // blue-500
    public static final Color DANGER  = new Color(0xDC2626); // red-600
    public static final Color SUCCESS = new Color(0x16A34A); // green-600

    // ── Fonts ─────────────────────────────────────────────────────────────────
    public static final Font FONT_TITLE   = new Font("Segoe UI", Font.BOLD,  22);
    public static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD,  14);
    public static final Font FONT_BODY    = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_SMALL   = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_MONO    = new Font("Consolas", Font.PLAIN, 12);

    // ── Spacing ───────────────────────────────────────────────────────────────
    public static final int GAP_SM = 8;
    public static final int GAP_MD = 14;
    public static final int GAP_LG = 20;

    private AppTheme() {}

    // ── Button factories ──────────────────────────────────────────────────────

    /**
     * Primary action button — uses the theme's default/accent fill via
     * {@code JButton.buttonType = "default"}.  Safe across all FlatLaf themes.
     */
    public static JButton primaryButton(String label) {
        JButton b = new JButton(label);
        b.putClientProperty("JButton.buttonType", "default");
        b.setFocusPainted(false);
        return b;
    }

    /**
     * Danger button — red fill via {@code FlatLaf.style} (correct FlatLaf API,
     * not {@code setBackground}).
     */
    public static JButton dangerButton(String label) {
        JButton b = new JButton(label);
        b.putClientProperty("FlatLaf.style",
                "background: #DC2626; foreground: #FFFFFF; " +
                "hoverBackground: #B91C1C; pressedBackground: #991B1B; " +
                "borderColor: #DC2626; hoverBorderColor: #B91C1C");
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setFocusPainted(false);
        return b;
    }

    /**
     * Secondary/neutral button — outlined round-rect, no fill override.
     * The active FlatLaf theme owns the exact color.
     */
    public static JButton secondaryButton(String label) {
        JButton b = new JButton(label);
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setFocusPainted(false);
        return b;
    }

    // ── Border factory ────────────────────────────────────────────────────────

    /**
     * Standard section border: titled border with inner padding.
     * No hardcoded line color — the theme owns the border color.
     */
    public static Border sectionBorder(String title) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                new EmptyBorder(8, 10, 8, 10)
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
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(0, 0, GAP_MD, 0));

        JLabel t = new JLabel(title);
        t.setFont(FONT_TITLE);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel s = new JLabel(subtitle);
        s.setFont(FONT_SMALL);
        // UIManager.getColor() is theme-adaptive — safe across all 32 themes
        s.setForeground(UIManager.getColor("Label.disabledForeground"));
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
     * <p>Call this once in {@code main()}, after the theme is applied and
     * before {@code SwingUtilities.invokeLater}.
     */
    public static void applyFlatLafTweaks() {
        UIManager.put("Component.arc",      8);
        UIManager.put("Button.arc",         8);
        UIManager.put("TextComponent.arc",  6);
        UIManager.put("ScrollBar.width",    8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("Table.rowHeight",    32);
        UIManager.put("TextField.margin",   new Insets(6, 8, 6, 8));
        UIManager.put("ComboBox.padding",   new Insets(4, 6, 4, 6));
        // No color-based UIManager.put() calls — theme owns all palette colors
    }
}
