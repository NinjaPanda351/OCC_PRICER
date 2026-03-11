package com.cardpricer.service;

import com.cardpricer.model.Card;
import com.cardpricer.model.TradeItem;
import com.cardpricer.util.CardConstants;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates POS inventory import CSV files from received trade items.
 *
 * <p>Output format: LINE NO,DEPARTMENT,CATEGORY,TYPE,CODE,ITEM TYPE,ORDER NO,
 * DESCRIPTION,UOM,QTY ON ORD,RESTOCK LEVEL,REORDER POINT,QTY ON HAND,COST,
 * DISCOUNT,BID,EXTENDED COST,TAX CODE,PRICE
 */
public final class PosExportService {

    private static final String DATA_DIRECTORY =
            com.cardpricer.util.AppDataDirectory.tradesPath();

    /**
     * Exports received cards to POS inventory import CSV format.
     *
     * @param items       list of received cards
     * @param traderName  name of the trader
     * @param customerName name of the customer
     * @param unitPrices  condition-adjusted market prices, one per item
     * @param quantities  quantities, one per item
     * @param paymentType "credit", "check", "partial", or "inventory"
     * @return absolute path of the generated file
     */
    public String exportToPOSFormat(List<TradeItem> items, String traderName, String customerName,
                                    List<BigDecimal> unitPrices, List<Integer> quantities,
                                    String paymentType) throws IOException {
        ensureDataDirExists();

        String timestamp  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String safeCustomer = sanitize(customerName.isEmpty() ? "UNKNOWN" : customerName).toUpperCase();
        String safeTrader   = sanitize(traderName.isEmpty()   ? "UNKNOWN" : traderName).toUpperCase();
        String filename = String.format("%s/%s_%s_%s.csv", DATA_DIRECTORY, timestamp, safeCustomer, safeTrader);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("LINE NO,DEPARTMENT,CATEGORY,TYPE,CODE,ITEM TYPE,ORDER NO," +
                    "DESCRIPTION,UOM,QTY ON ORD,RESTOCK LEVEL,REORDER POINT," +
                    "QTY ON HAND,COST,DISCOUNT,BID,EXTENDED COST,TAX CODE,PRICE");

            int lineNo = 1;
            for (int i = 0; i < items.size(); i++) {
                TradeItem item  = items.get(i);
                Card      card  = item.getCard();

                String code = card.getSetCode() + " " + card.getCollectorNumber()
                        + (item.isFoil() ? "F" : "");

                StringBuilder desc = new StringBuilder(card.getName());
                if (card.getFrameEffectDisplay() != null) desc.append(" - ").append(card.getFrameEffects());
                if (item.isFoil()) desc.append(" (Foil)");

                int        qty   = quantities.get(i);
                BigDecimal price = unitPrices.get(i);
                BigDecimal cost;
                if ("inventory".equalsIgnoreCase(paymentType)) {
                    cost = BigDecimal.ZERO;
                } else if ("check".equalsIgnoreCase(paymentType)) {
                    cost = price.divide(CardConstants.PAYMENT_DIVISOR_CHECK, 2, RoundingMode.HALF_UP);
                } else if ("partial".equalsIgnoreCase(paymentType)) {
                    cost = price.multiply(CardConstants.PAYMENT_RATE_PARTIAL).setScale(2, RoundingMode.HALF_UP);
                } else {
                    cost = price.multiply(CardConstants.PAYMENT_RATE_CREDIT).setScale(2, RoundingMode.HALF_UP);
                }

                writer.printf("%d,5,5.2,,%s,,%s,%s,,%d,,,%.2f,,,%.2f,TAX,%.2f%n",
                        lineNo++, code, "", quoteIfNeeded(escapeCSV(desc.toString())),
                        qty, cost, cost.multiply(BigDecimal.valueOf(qty)), price);
            }
        }

        System.out.println("POS import file created: " + filename);
        SharedFolderService.copyToSharedFolder(filename);
        return filename;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static void ensureDataDirExists() {
        new java.io.File(DATA_DIRECTORY).mkdirs();
    }

    static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "unknown";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }

    private static String escapeCSV(String value) {
        if (value == null) return "";
        return value.replace(",", "ɕ").replace("\"", "\"\"");
    }

    private static String quoteIfNeeded(String value) {
        if (value == null) return "";
        String escaped = escapeCSV(value);
        return escaped.contains("\"") ? "\"" + escaped + "\"" : escaped;
    }
}
