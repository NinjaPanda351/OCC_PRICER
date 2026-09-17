package com.cardpricer.service;

import com.cardpricer.model.TradeItem;
import com.cardpricer.util.SetList;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** The provisional 19-column receiving schema. Consumes approved costs without repricing. */
public final class TradePosEncoder {
    private TradePosEncoder() {}
    private enum Column {
        LINE_NO, DEPARTMENT, CATEGORY, TYPE, CODE, ITEM_TYPE, ORDER_NO, DESCRIPTION, UOM,
        QTY_ON_ORD, RESTOCK_LEVEL, REORDER_POINT, QTY_ON_HAND, COST, DISCOUNT, BID,
        EXTENDED_COST, TAX_CODE, PRICE;

        String header() { return name().replace('_', ' '); }
    }
    public static void write(Writer writer, List<TradeItem> items, List<BigDecimal> unitPrices,
                             SettlementEngine.Settlement settlement) throws IOException {
        if (items.size() != settlement.lines().size() || items.size() != unitPrices.size())
            throw new IllegalArgumentException("Settlement and item counts differ");
        CsvRows.write(writer, (Object[]) java.util.Arrays.stream(Column.values()).map(Column::header).toArray(String[]::new));
        int line = 1;
        java.util.Map<String,com.cardpricer.model.PrintingIdentity> identities=new java.util.HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            var allocation = settlement.lines().get(i);
            if (!allocation.line().stock()) continue;
            var item = items.get(i);
            String mapped=SetList.fromScryfallCode(item.getCard().getSetCode())+" "+item.getCard().getCollectorNumber()+item.getFinishType();
            var previous=identities.putIfAbsent(mapped,item.getCard().identity());
            if (previous!=null && !previous.equals(item.getCard().identity()))
                throw new IllegalArgumentException("POS mapping merges different printings: "+mapped);
            int quantity = allocation.line().quantity();
            BigDecimal unitCost = allocation.cost().divide(BigDecimal.valueOf(quantity), 2, RoundingMode.DOWN);
            int extra = allocation.cost().subtract(unitCost.multiply(BigDecimal.valueOf(quantity)))
                    .movePointRight(2).intValueExact();
            // Split a quantity only when a cent residual cannot be represented by one unit cost.
            if (quantity > extra) writeRow(writer, line++, item, quantity - extra, unitCost, unitPrices.get(i));
            if (extra > 0) writeRow(writer, line++, item, extra, unitCost.add(new BigDecimal("0.01")), unitPrices.get(i));
        }
    }
    private static void writeRow(Writer writer, int line, TradeItem item, int qty,
                                 BigDecimal cost, BigDecimal price) throws IOException {
        var card = item.getCard();
        String code = SetList.fromScryfallCode(card.getSetCode()) + " " + card.getCollectorNumber() + item.getFinishType();
        // The receiving POS requires U+0255 instead of embedded commas, even in quoted fields.
        String description = card.getName().replace(',', '\u0255')
                + (item.isFoil() ? " (" + item.getFinish() + ")" : "");
        // Every column exists, including unused fields such as QTY ON HAND.
        // Assign values by column name so adding a value cannot shift the cost/tax/price fields.
        Object[] row = new Object[Column.values().length];
        java.util.Arrays.fill(row, "");
        row[Column.LINE_NO.ordinal()] = line;
        row[Column.DEPARTMENT.ordinal()] = "5";
        row[Column.CATEGORY.ordinal()] = "5.2";
        row[Column.CODE.ordinal()] = code;
        row[Column.DESCRIPTION.ordinal()] = description;
        row[Column.QTY_ON_ORD.ordinal()] = qty;
        row[Column.COST.ordinal()] = cost.toPlainString();
        row[Column.EXTENDED_COST.ordinal()] = cost.multiply(BigDecimal.valueOf(qty)).toPlainString();
        row[Column.TAX_CODE.ordinal()] = "TAX";
        row[Column.PRICE.ordinal()] = price.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        CsvRows.write(writer, row);
    }
}
