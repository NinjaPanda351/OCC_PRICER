package com.cardpricer.gui.panel.trade;

import javax.swing.table.DefaultTableModel;

/**
 * Table model for the trade-receiving table.
 *
 * <p>Named column constants replace magic numbers throughout TradePanel.
 */
public final class TradeTableModel extends DefaultTableModel {

    public static final int COL_CHECK      = 0;
    public static final int COL_CODE       = 1;
    public static final int COL_NAME       = 2;
    public static final int COL_CONDITION  = 3;
    public static final int COL_QTY        = 4;
    public static final int COL_UNIT_PRICE = 5;
    public static final int COL_TOTAL      = 6;
    public static final int COL_RATE       = 7;

    private static final String[] COLUMNS =
            {"☑", "Code", "Card Name", "Condition", "Qty", "Unit Price", "Total", "Rate"};

    public TradeTableModel() {
        super(COLUMNS, 0);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == COL_CHECK || column == COL_CONDITION
                || column == COL_QTY || column == COL_UNIT_PRICE;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case COL_CHECK     -> Boolean.class;
            case COL_QTY       -> Integer.class;
            default            -> String.class;
        };
    }
}
