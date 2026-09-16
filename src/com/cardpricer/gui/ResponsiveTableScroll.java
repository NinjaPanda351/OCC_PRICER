package com.cardpricer.gui;

import com.cardpricer.util.AppTheme;
import javax.swing.*;
import javax.swing.table.TableColumn;

/** Expands a table on wide screens and enables horizontal scrolling before columns become unreadable. */
public final class ResponsiveTableScroll extends JScrollPane {
    private final JTable table;
    public ResponsiveTableScroll(JTable table, int... minimumWidths) {
        super(table); this.table = table;
        setColumnHeaderView(table.getTableHeader()); setBorder(AppTheme.cardBorder(0));
        for (int i = 0; i < minimumWidths.length && i < table.getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            int minimum = com.formdev.flatlaf.util.UIScale.scale(minimumWidths[i]);
            column.setMinWidth(minimum);
            column.setPreferredWidth(Math.max(minimum, column.getPreferredWidth()));
        }
    }
    @Override public void doLayout() {
        if (table != null) {
            int minimum = 0;
            for (int i = 0; i < table.getColumnCount(); i++) minimum += table.getColumnModel().getColumn(i).getMinWidth();
            int available = getWidth() - getInsets().left - getInsets().right - getVerticalScrollBar().getPreferredSize().width;
            int mode = available < minimum ? JTable.AUTO_RESIZE_OFF : JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS;
            if (table.getAutoResizeMode() != mode) table.setAutoResizeMode(mode);
        }
        super.doLayout();
    }
}
