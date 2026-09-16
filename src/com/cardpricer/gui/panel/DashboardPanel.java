package com.cardpricer.gui.panel;

import com.cardpricer.gui.AppIcon;
import com.cardpricer.util.AppTheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;
import java.util.function.Consumer;

/** Action-first workspace, with no fabricated activity or financial figures. */
public final class DashboardPanel extends JPanel {
    public DashboardPanel(Consumer<String> navigate) {
        super(new BorderLayout());
        setBorder(new EmptyBorder(18, 20, 18, 20));
        JPanel content = new com.cardpricer.gui.ScrollablePage();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JPanel header = AppTheme.panelHeader("Overview", "Price cards, manage trade-ins, and update inventory.");
        content.add(aligned(header));
        content.add(Box.createVerticalStrut(22));

        CardArtwork artwork = new CardArtwork();
        JPanel hero = AppTheme.surface(new BorderLayout(20, 0) {
            @Override public void layoutContainer(Container parent) {
                artwork.setVisible(parent.getWidth() >= com.formdev.flatlaf.util.UIScale.scale(820));
                super.layoutContainer(parent);
            }
        }, 24);
        hero.setPreferredSize(new Dimension(750, com.formdev.flatlaf.util.UIScale.scale(244)));
        JPanel message = AppTheme.transparent(null);
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        message.add(AppTheme.eyebrow("THE TRADE COUNTER"));
        message.add(Box.createVerticalStrut(16));
        JLabel headline = new JLabel("Ready for the next trade.");
        headline.setFont(AppTheme.FONT_TITLE);
        message.add(headline);
        message.add(Box.createVerticalStrut(12));
        message.add(AppTheme.mutedLabel("Find the right printing. Build a clear offer."));
        message.add(Box.createVerticalStrut(5));
        message.add(AppTheme.mutedLabel("Keep every card and every payout accounted for."));
        message.add(Box.createVerticalGlue());
        JPanel actions = AppTheme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton start = AppTheme.primaryButton("Start a trade");
        start.setIcon(new AppIcon(AppIcon.Kind.PLUS));
        start.addActionListener(e -> navigate.accept("trades"));
        actions.add(start);
        actions.add(Box.createHorizontalStrut(15));
        JButton history = AppTheme.secondaryButton("Trade history");
        history.addActionListener(e -> navigate.accept("history"));
        actions.add(history);
        message.add(actions);
        for (Component child : message.getComponents()) if (child instanceof JComponent c) c.setAlignmentX(Component.LEFT_ALIGNMENT);
        hero.add(message, BorderLayout.CENTER);
        hero.add(artwork, BorderLayout.EAST);
        content.add(aligned(hero));
        content.add(Box.createVerticalStrut(28));

        PriceCheckPanel priceCheck=new PriceCheckPanel();
        priceCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(priceCheck);
        content.add(Box.createVerticalStrut(28));

        JPanel toolsHeading = AppTheme.transparent(new BorderLayout());
        JLabel label = new JLabel("Workspace tools"); label.setFont(AppTheme.FONT_HEADING);
        toolsHeading.add(label, BorderLayout.WEST);
        toolsHeading.add(AppTheme.mutedLabel("Your daily tools"), BorderLayout.EAST);
        content.add(aligned(toolsHeading));
        content.add(Box.createVerticalStrut(14));
        JPanel tools = new com.cardpricer.gui.ResponsiveGrid(3, 260, 14);
        tools.add(tool("Set pricer", "Fresh prices. Ready to export.", "Price entire sets and prepare your POS files.", AppIcon.Kind.STACK, () -> navigate.accept("bulk")));
        tools.add(tool("Inventory", "A clear view of your stock.", "Update quantities for each printing and finish.", AppIcon.Kind.INVENTORY, () -> navigate.accept("inventory")));
        tools.add(tool("Files & history", "Every saved trade, in one place.", "Find exports, review receipts, and print copies.", AppIcon.Kind.FILES, () -> navigate.accept("files")));
        tools.setAlignmentX(Component.LEFT_ALIGNMENT); content.add(tools);
        content.add(Box.createVerticalStrut(22));
        JPanel tip = AppTheme.transparent(new com.cardpricer.gui.WrapLayout(FlowLayout.LEFT, 10, 4));
        tip.add(AppTheme.mutedLabel("Price check shortcuts:"));
        tip.add(AppTheme.mutedLabel("F2 / Ctrl+F  Search by name"));
        tip.add(AppTheme.mutedLabel("Enter  Check card code"));
        content.add(aligned(tip));
        content.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(content);
        scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll);
    }
    private static JComponent aligned(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }
    private static JPanel tool(String title, String subtitle, String description, AppIcon.Kind icon, Runnable action) {
        JPanel panel = AppTheme.surface(new BorderLayout(0, 16), 20);
        panel.setPreferredSize(new Dimension(260, com.formdev.flatlaf.util.UIScale.scale(190)));
        JPanel top = AppTheme.transparent(new BorderLayout());
        JLabel symbol = new JLabel(new AppIcon(icon, 25)); symbol.setForeground(AppTheme.accent());
        top.add(symbol, BorderLayout.WEST);
        JButton open = AppTheme.secondaryButton(""); open.setIcon(new AppIcon(AppIcon.Kind.ARROW, 18));
        open.setMargin(new Insets(6, 6, 6, 6)); open.setToolTipText("Open " + title);
        open.getAccessibleContext().setAccessibleName("Open " + title);
        open.addActionListener(e -> action.run()); top.add(open, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        JPanel copy = AppTheme.transparent(null); copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JButton heading = new JButton(title);
        heading.setFont(AppTheme.FONT_HEADING.deriveFont(17f)); heading.setBorder(null); heading.setContentAreaFilled(false);
        heading.setHorizontalAlignment(SwingConstants.LEFT); heading.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        heading.addActionListener(e -> action.run()); heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.add(heading); copy.add(Box.createVerticalStrut(8));
        JLabel sub = AppTheme.mutedLabel(subtitle); sub.setAlignmentX(Component.LEFT_ALIGNMENT); copy.add(sub);
        copy.add(Box.createVerticalStrut(6));
        JLabel text = AppTheme.mutedLabel("<html>" + description + "</html>");
        text.setPreferredSize(new Dimension(180, 40));
        text.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        text.setAlignmentX(Component.LEFT_ALIGNMENT); copy.add(text);
        panel.add(copy, BorderLayout.CENTER);
        return panel;
    }

    /** Decorative card outlines, painted at any display scale without external assets. */
    private static final class CardArtwork extends JComponent {
        CardArtwork() { setPreferredSize(new Dimension(260, 210)); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(getWidth() / 2d, getHeight() / 2d);
            double scale = Math.min(getWidth() / 280d, getHeight() / 225d);
            g.scale(scale, scale);
            for (int i = 0; i < 3; i++) {
                Graphics2D card = (Graphics2D) g.create();
                card.translate((i - 1) * 44, i == 1 ? -4 : 10); card.rotate((i - 1) * .19);
                card.setColor(new Color(0, 0, 0, 35)); card.fillRoundRect(-66, -91, 140, 186, 14, 14);
                card.setColor(AppTheme.color("OCC.raisedSurface", new Color(0xE4EBF5))); card.fillRoundRect(-70, -98, 140, 186, 14, 14);
                card.setColor(AppTheme.border()); card.drawRoundRect(-70, -98, 140, 186, 14, 14);
                card.setPaint(new GradientPaint(-52, -68, AppTheme.color("OCC.accentSurface", new Color(0xCAD8EF)), 54, 39, AppTheme.surface()));
                card.fillRoundRect(-54, -67, 108, 100, 7, 7);
                card.setColor(new Color(131, 169, 244, i == 1 ? 150 : 70));
                card.setStroke(new BasicStroke(1.2f));
                card.draw(new Ellipse2D.Double(-27, -45, 54, 54)); card.draw(new Ellipse2D.Double(-17, -35, 34, 34));
                Path2D diamond = new Path2D.Double(); diamond.moveTo(0, -57); diamond.lineTo(36, -18); diamond.lineTo(0, 21); diamond.lineTo(-36, -18); diamond.closePath(); card.draw(diamond);
                card.setColor(AppTheme.muted()); card.fillRoundRect(-51, -84, 53, 4, 4, 4);
                card.setColor(AppTheme.border()); card.fillRoundRect(-51, 48, 76, 4, 4, 4); card.fillRoundRect(-51, 60, 97, 4, 4, 4);
                card.dispose();
            }
            g.dispose();
        }
    }
}
