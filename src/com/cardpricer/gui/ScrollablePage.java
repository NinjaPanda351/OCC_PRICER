package com.cardpricer.gui;

import javax.swing.*;
import java.awt.*;

/** Keeps a settings/dashboard page as wide as its viewport and scrolls vertically. */
public final class ScrollablePage extends JPanel implements Scrollable {
    public ScrollablePage() { setOpaque(false); }
    public static JScrollPane wrap(JComponent content) {
        ScrollablePage page = new ScrollablePage();
        page.setLayout(new BorderLayout()); page.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(page);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }
    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(24, visible.height - 24); }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return getParent() != null && getPreferredSize().height < getParent().getHeight(); }
}
