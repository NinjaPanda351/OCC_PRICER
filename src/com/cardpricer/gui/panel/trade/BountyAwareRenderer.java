package com.cardpricer.gui.panel.trade;

import com.cardpricer.model.TradeRow;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Table cell renderer that tints rows whose payout came from a bounty override.
 *
 * <p>Bounty rows are rendered with a muted teal background ({@code #2A7A7A}) and white
 * foreground; all other rows use the table's default colors.
 */
public final class BountyAwareRenderer extends DefaultTableCellRenderer {

    private final Supplier<List<TradeRow>> rowsSupplier;

    public BountyAwareRenderer(Supplier<List<TradeRow>> rowsSupplier) {
        this.rowsSupplier = rowsSupplier;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, column);
        if (!isSelected) {
            int modelRow = table.convertRowIndexToModel(row);
            List<TradeRow> rows = rowsSupplier.get();
            if (modelRow >= 0 && modelRow < rows.size()
                    && rows.get(modelRow).payout != null
                    && rows.get(modelRow).payout.isBounty()) {
                c.setBackground(new Color(42, 122, 122));
                c.setForeground(Color.WHITE);
            } else {
                c.setBackground(table.getBackground());
                c.setForeground(table.getForeground());
            }
        }
        return c;
    }
}
