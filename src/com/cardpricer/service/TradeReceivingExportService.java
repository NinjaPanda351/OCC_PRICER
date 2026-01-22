package com.cardpricer.service;

import com.cardpricer.model.Card;
import com.cardpricer.model.TradeItem;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for exporting received cards to POS import format
 */
public class TradeReceivingExportService {

    private static final String DATA_DIRECTORY = "data/trades";

    private void ensureDataDirectoryExists() {
        java.io.File dataDir = new java.io.File(DATA_DIRECTORY);
        if (!dataDir.exists()) {
            dataDir.mkdir();
        }
    }

    /**
     * Exports received cards to POS inventory import CSV format
     * Format: LINE NO,DEPARTMENT,CATEGORY,TYPE,CODE,ITEM TYPE,ORDER NO,DESCRIPTION,UOM,
     *         QTY ON ORD,RESTOCK LEVEL,REORDER POINT,QTY ON HAND,COST,DISCOUNT,BID,
     *         EXTENDED COST,TAX CODE,PRICE
     *
     * Required fields: CODE, QTY ON ORD, COST
     *
     * @param items List of received cards
     * @param traderName Name of trader/source
     * @return The filename of the exported CSV
     */
    public String exportToPOSFormat(List<TradeItem> items, String traderName) throws IOException {
        ensureDataDirectoryExists();

        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safeName = sanitizeFilename(traderName);
        String filename = String.format("%s/pos_import_%s_%s.csv",
                DATA_DIRECTORY, timestamp, safeName);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Write header
            writer.println("LINE NO,DEPARTMENT,CATEGORY,TYPE,CODE,ITEM TYPE,ORDER NO," +
                    "DESCRIPTION,UOM,QTY ON ORD,RESTOCK LEVEL,REORDER POINT," +
                    "QTY ON HAND,COST,DISCOUNT,BID,EXTENDED COST,TAX CODE,PRICE");

            int lineNo = 1;
            for (TradeItem item : items) {
                Card card = item.getCard();

                // Build code with finish indicator
                StringBuilder codeBuilder = new StringBuilder();
                codeBuilder.append(card.getSetCode())
                        .append(" ")
                        .append(card.getCollectorNumber());

                if (item.isFoil()) {
                    codeBuilder.append("F"); // Generic foil/etched indicator
                }

                String code = codeBuilder.toString();

                // Build description
                StringBuilder descBuilder = new StringBuilder();
                descBuilder.append(card.getName());

                if (card.getFrameEffectDisplay()!= null) {
                    descBuilder.append(" - ").append(card.getFrameEffects());
                }

                if (item.isFoil()) {
                    descBuilder.append(" (Foil)");
                }

                String description = escapeCSV(descBuilder.toString());

                int qtyOnOrder = item.getQuantity();

                // Apply pricing rules
                BigDecimal roundedPrice = applyPricingRules(item.getUnitPrice(), card.getRarity());
                BigDecimal cost = roundedPrice;
                BigDecimal price = roundedPrice;
                BigDecimal extendedCost = cost.multiply(BigDecimal.valueOf(qtyOnOrder));

                // LINE NO,DEPARTMENT,CATEGORY,TYPE,CODE,ITEM TYPE,ORDER NO,DESCRIPTION,UOM,
                // QTY ON ORD,RESTOCK LEVEL,REORDER POINT,QTY ON HAND,COST,DISCOUNT,BID,
                // EXTENDED COST,TAX CODE,PRICE
                writer.printf("%d,5,5.2,,%s,,%s,%s,,%d,,,%.2f,,,%.2f,TAX,%.2f%n",
                        lineNo++,               // LINE NO
                        code,                   // CODE (required)
                        "",                     // ORDER NO (can be populated with trader name if needed)
                        quoteIfNeeded(description), // DESCRIPTION
                        qtyOnOrder,             // QTY ON ORD (required)
                        cost,                   // COST (required)
                        extendedCost,           // EXTENDED COST
                        price                   // PRICE
                );
            }
        }

        System.out.println("POS import file created: " + filename);
        return filename;
    }

    /**
     * Applies pricing rules: minimums and rounding
     */
    private BigDecimal applyPricingRules(BigDecimal price, String rarity) {
        BigDecimal minimum = getMinimumByRarity(rarity);
        BigDecimal priceToRound = price.max(minimum);

        if (priceToRound.compareTo(BigDecimal.TEN) < 0) {
            // Below $10 - round to nearest $0.50
            BigDecimal rounded = priceToRound.divide(new BigDecimal("0.5"), 0, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("0.5"));
            return rounded.max(minimum);
        } else {
            // $10 and above - round to nearest $1.00
            return priceToRound.setScale(0, java.math.RoundingMode.HALF_UP);
        }
    }

    private BigDecimal getMinimumByRarity(String rarity) {
        if (rarity == null) {
            return new BigDecimal("0.10");
        }

        String rarityLower = rarity.toLowerCase();

        if (rarityLower.equals("rare") || rarityLower.equals("mythic")) {
            return new BigDecimal("0.50");
        } else if (rarityLower.equals("uncommon")) {
            return new BigDecimal("0.25");
        } else {
            return new BigDecimal("0.10");
        }
    }

    /**
     * Saves a human-readable card list for record keeping
     *
     * @param items List of received cards
     * @param traderName Name of trader/source
     * @return The filename of the saved list
     */
    public String saveCardList(List<TradeItem> items, String traderName) throws IOException {
        ensureDataDirectoryExists();

        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safeName = sanitizeFilename(traderName);
        String filename = String.format("%s/cardlist_%s_%s.txt",
                DATA_DIRECTORY, timestamp, safeName);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("╔════════════════════════════════════════════════════════════╗");
            writer.println("║          RECEIVED CARDS LIST - OCC CARD PRICER             ║");
            writer.println("╚════════════════════════════════════════════════════════════╝");
            writer.println();

            writer.println("Source: " + traderName);
            writer.println("Date: " + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            writer.println("Total Cards: " + items.size());

            BigDecimal totalValue = BigDecimal.ZERO;
            for (TradeItem item : items) {
                Card card = item.getCard();
                BigDecimal roundedPrice = applyPricingRules(item.getUnitPrice(), card.getRarity());
                totalValue = totalValue.add(roundedPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
            }

            writer.printf("Total Value: $%.2f%n", totalValue);
            writer.printf("Half Rate (50%%): $%.2f%n",
                    totalValue.multiply(new BigDecimal("0.50")));
            writer.printf("Third Rate (33%%): $%.2f%n",
                    totalValue.multiply(new BigDecimal("0.33")));

            writer.println("\n" + "=".repeat(70));
            writer.println("CARD LIST");
            writer.println("=".repeat(70));
            writer.printf("%-15s %-40s %-10s%n", "Code", "Card Name", "Price");
            writer.println("-".repeat(70));

            for (TradeItem item : items) {
                Card card = item.getCard();

                StringBuilder codeBuilder = new StringBuilder();
                codeBuilder.append(card.getSetCode())
                        .append(" ")
                        .append(card.getCollectorNumber());
                if (item.isFoil()) {
                    codeBuilder.append("F");
                }

                StringBuilder nameBuilder = new StringBuilder();
                nameBuilder.append(card.getName());
                if (card.getFrameEffectDisplay() != null) {
                    nameBuilder.append(" - ").append(card.getFrameEffects());
                }
                if (item.isFoil()) {
                    nameBuilder.append(" (Foil)");
                }

                // Apply pricing rules
                BigDecimal roundedPrice = applyPricingRules(item.getUnitPrice(), card.getRarity());

                writer.printf("%-15s %-40s $%-9.2f%n",
                        codeBuilder.toString(),
                        truncate(nameBuilder.toString(), 40),
                        roundedPrice);
            }

            writer.println("-".repeat(70));
            writer.printf("%56s $%-9.2f%n", "TOTAL:", totalValue);

            writer.println("\n" + "=".repeat(70));
            writer.println("Generated by OCC Card Pricer");
            writer.println(LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        System.out.println("Card list saved: " + filename);
        return filename;
    }

    /**
     * Sanitizes a filename by removing invalid characters
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unknown";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }

    /**
     * Truncates a string to a maximum length
     */
    private String truncate(String str, int length) {
        if (str == null || str.length() <= length) {
            return str;
        }
        return str.substring(0, length - 3) + "...";
    }

    /**
     * Escapes special characters in CSV values
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\"\"");
    }

    /**
     * Quotes a string if it contains commas
     */
    private String quoteIfNeeded(String value) {
        if (value == null) {
            return "";
        }
        String escaped = escapeCSV(value);
        if (escaped.contains(",")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}