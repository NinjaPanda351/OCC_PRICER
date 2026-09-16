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
    public static void write(Writer writer, List<TradeItem> items, List<BigDecimal> unitPrices,
                             SettlementEngine.Settlement settlement) throws IOException {
        if (items.size() != settlement.lines().size() || items.size() != unitPrices.size())
            throw new IllegalArgumentException("Settlement and item counts differ");
        CsvRows.writeRow(writer, "LINE NO,DEPARTMENT,CATEGORY,TYPE,CODE,ITEM TYPE,ORDER NO,DESCRIPTION,UOM,"
                + "QTY ON ORD,RESTOCK LEVEL,REORDER POINT,QTY ON HAND,COST,DISCOUNT,BID,EXTENDED COST,TAX CODE,PRICE");
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
        String description = card.getName() + (item.isFoil() ? " (" + item.getFinish() + ")" : "");
        CsvRows.write(writer, line, "5", "5.2", "", code, "", "", description, "", qty, "", "", "",
                cost.toPlainString(), "", "", cost.multiply(BigDecimal.valueOf(qty)).toPlainString(),
                "TAX", price.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
    }
}
