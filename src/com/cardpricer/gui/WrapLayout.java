package com.cardpricer.gui;

import java.awt.*;

/** Flow layout whose preferred height includes every wrapped row. */
public final class WrapLayout extends FlowLayout {
    public WrapLayout(int alignment, int horizontalGap, int verticalGap) {
        super(alignment, horizontalGap, verticalGap);
    }

    @Override public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int width = target.getWidth();
            if (width <= 0 && target.getParent() != null) width = target.getParent().getWidth();
            int available = width > 0 ? width - insets.left - insets.right - 2 * getHgap() : Integer.MAX_VALUE;
            int rowWidth = 0, rowHeight = 0, totalWidth = 0, totalHeight = 0;
            for (Component child : target.getComponents()) {
                if (!child.isVisible()) continue;
                Dimension size = child.getPreferredSize();
                int gap = rowWidth == 0 ? 0 : getHgap();
                if (rowWidth > 0 && rowWidth + gap + size.width > available) {
                    totalWidth = Math.max(totalWidth, rowWidth);
                    totalHeight += rowHeight + getVgap();
                    rowWidth = 0; rowHeight = 0; gap = 0;
                }
                rowWidth += gap + size.width;
                rowHeight = Math.max(rowHeight, size.height);
            }
            return new Dimension(Math.max(totalWidth, rowWidth) + insets.left + insets.right + 2 * getHgap(),
                    totalHeight + rowHeight + insets.top + insets.bottom + 2 * getVgap());
        }
    }
    @Override public Dimension minimumLayoutSize(Container target) { return new Dimension(0, preferredLayoutSize(target).height); }
}
