package com.cardpricer.gui;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/** Equal-width cards or fields, using fewer columns when the content gets narrow. */
public final class ResponsiveGrid extends JPanel {
    private final int maximumColumns;
    private final int minimumCellWidth;
    private final int gap;

    public ResponsiveGrid(int maximumColumns, int minimumCellWidth, int gap) {
        super(null); setOpaque(false);
        this.maximumColumns = maximumColumns; this.minimumCellWidth = minimumCellWidth; this.gap = gap;
    }
    private Component[] items() { return Arrays.stream(getComponents()).filter(Component::isVisible).toArray(Component[]::new); }
    private int columns(int width, int count) {
        int minimum = com.formdev.flatlaf.util.UIScale.scale(minimumCellWidth);
        return Math.max(1, Math.min(Math.min(count, maximumColumns), (Math.max(0, width) + gap) / (minimum + gap)));
    }
    @Override public Dimension getPreferredSize() {
        Insets insets = getInsets();
        Component[] items = items();
        int width = getWidth() > 0 ? getWidth() : (getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : maximumColumns * minimumCellWidth);
        int columns = columns(width - insets.left - insets.right, items.length);
        int height = 0;
        for (int i = 0; i < items.length; i += columns) {
            int rowHeight = 0;
            for (int j = i; j < Math.min(i + columns, items.length); j++) rowHeight = Math.max(rowHeight, items[j].getPreferredSize().height);
            height += rowHeight + (i == 0 ? 0 : gap);
        }
        return new Dimension(width, height + insets.top + insets.bottom);
    }
    @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    @Override public void setBounds(int x, int y, int width, int height) {
        boolean changed = width != getWidth(); super.setBounds(x, y, width, height);
        if (changed) revalidate();
    }
    @Override public void doLayout() {
        Insets insets = getInsets(); Component[] items = items();
        int width = getWidth() - insets.left - insets.right;
        int columns = columns(width, items.length);
        int cellWidth = Math.max(0, (width - (columns - 1) * gap) / columns);
        int y = insets.top;
        for (int i = 0; i < items.length; i += columns) {
            int rowHeight = 0;
            for (int j = i; j < Math.min(i + columns, items.length); j++) rowHeight = Math.max(rowHeight, items[j].getPreferredSize().height);
            for (int j = i; j < Math.min(i + columns, items.length); j++) items[j].setBounds(insets.left + (j - i) * (cellWidth + gap), y, cellWidth, rowHeight);
            y += rowHeight + gap;
        }
    }
}
