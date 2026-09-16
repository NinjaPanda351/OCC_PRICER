package com.cardpricer.gui.panel.trade;

import com.cardpricer.model.*;
import com.cardpricer.service.BuyRateService;
import com.cardpricer.service.PricingService;
import javax.swing.table.DefaultTableModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** One typed row owns quantity, condition, valuation and payout. List APIs are read views. */
public final class TradeTableModel extends DefaultTableModel {
    private static final String[] COLUMNS = {"", "Code", "Card Name", "Condition", "Qty", "Unit Price", "Total", "Rate"};
    private final List<Row> rows = new ArrayList<>();
    private final PricingService pricing = new PricingService();
    private static final class Row {
        final TradeItem item;
        Condition condition;
        BigDecimal value;
        boolean selected;
        String rate = "";
        BuyRateService.PayoutResult payout;
        Row(TradeItem item, Condition condition, BigDecimal value) { this.item=item; this.condition=condition; this.value=value; }
    }
    public void addTrade(TradeItem item, String condition, BigDecimal value) {
        Row row = new Row(item, Condition.valueOf(condition), money(value));
        rows.add(row); fireTableRowsInserted(rows.size()-1, rows.size()-1);
    }
    public BigDecimal priceAt(int row) { return rows.get(row).value; }
    public BigDecimal totalAt(int row) { return priceAt(row).multiply(BigDecimal.valueOf(rows.get(row).item.getQuantity())); }
    public UUID idAt(int row) { return rows.get(row).item.getLineId(); }
    public int indexOf(UUID id) {
        for (int i=0;i<rows.size();i++) if (idAt(i).equals(id)) return i;
        return -1;
    }
    public List<TradeItem> items() { return new AbstractList<>() {
        public TradeItem get(int i) { return rows.get(i).item; } public int size() { return rows.size(); }
    }; }
    public List<String> conditions() { return new AbstractList<>() {
        public String get(int i) { return rows.get(i).condition.name(); } public int size() { return rows.size(); }
        public String set(int i, String condition) { String old=get(i); rows.get(i).condition=Condition.valueOf(condition); return old; }
    }; }
    public List<BuyRateService.PayoutResult> payouts() { return new AbstractList<>() {
        public BuyRateService.PayoutResult get(int i) { return rows.get(i).payout; } public int size() { return rows.size(); }
        public BuyRateService.PayoutResult set(int i, BuyRateService.PayoutResult value) {
            var old=get(i); rows.get(i).payout=value; return old;
        }
    }; }
    @Override public int getRowCount() { return rows == null ? 0 : rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }
    @Override public Class<?> getColumnClass(int column) {
        return switch(column) { case 0 -> Boolean.class; case 4 -> Integer.class; default -> String.class; };
    }
    @Override public boolean isCellEditable(int row, int column) { return column==0 || column==3 || column==4 || column==5; }
    @Override public Object getValueAt(int index, int column) {
        Row row=rows.get(index);
        return switch(column) {
            case 0 -> row.selected;
            case 1 -> row.item.getSetCollectorCode();
            case 2 -> row.item.getCard().getName() + (row.item.isFoil() ? " ("+row.item.getFinish()+")" : "");
            case 3 -> row.condition.name();
            case 4 -> row.item.getQuantity();
            case 5 -> "$"+row.value.toPlainString();
            case 6 -> "$"+totalAt(index).toPlainString();
            case 7 -> row.rate;
            default -> throw new IndexOutOfBoundsException(column);
        };
    }
    @Override public void setValueAt(Object value, int index, int column) {
        Row row=rows.get(index);
        switch(column) {
            case 0 -> row.selected=Boolean.TRUE.equals(value);
            case 3 -> {
                row.condition=Condition.valueOf(value.toString());
                BigDecimal base=pricing.applyPricingRules(row.item.getUnitPrice(), row.item.getCard().getRarity());
                row.value=money(pricing.applyConditionMultiplier(base,row.condition.name()));
                if (row.condition.name().equals(row.item.getManualCondition())) row.value=row.item.getManualPrice();
            }
            case 4 -> row.item.setQuantity(Integer.parseInt(value.toString()));
            case 5 -> {
                row.value=money(new BigDecimal(value.toString().replace("$", "").trim()));
                row.item.setManualOverride(row.condition.name(),row.value);
            }
            case 6 -> { return; } // Derived total has no independent state.
            case 7 -> row.rate=Objects.toString(value, "");
            default -> throw new IllegalArgumentException("Read-only column");
        }
        fireTableCellUpdated(index,column);
        if (column==3 || column==4 || column==5) {
            fireTableCellUpdated(index,6);
        }
    }
    @Override public void removeRow(int index) { rows.remove(index); fireTableRowsDeleted(index,index); }
    @Override public void setRowCount(int count) {
        if (rows==null) return;
        if (count!=0) throw new IllegalArgumentException("Use addTrade");
        rows.clear(); fireTableDataChanged();
    }
    private static BigDecimal money(BigDecimal value) {
        if (value.signum()<0) throw new IllegalArgumentException("Price must be non-negative");
        return value.setScale(2,RoundingMode.UNNECESSARY);
    }
}
