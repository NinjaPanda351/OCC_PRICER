package com.cardpricer.service;

import com.cardpricer.model.Card;
import com.cardpricer.model.TradeItem;
import com.cardpricer.util.CardConstants;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for exporting received cards to POS import format.
 * Pricing logic is delegated to {@link PricingService}; payment and condition
 * constants are sourced from {@link CardConstants}.
 */
public class TradeReceivingExportService {

    private static final String DATA_DIRECTORY = "data/trades";

    private final PricingService pricingService = new PricingService();

    private void ensureDataDirectoryExists() {
        java.io.File dataDir = new java.io.File(DATA_DIRECTORY);
        if (!dataDir.exists()) {
            dataDir.mkdirs(); // Use mkdirs() to create parent directories too
        }
    }

    /**
     * Exports received cards to POS inventory import CSV format using table values.
     *
     * <p>Format: LINE NO,DEPARTMENT,CATEGORY,TYPE,CODE,ITEM TYPE,ORDER NO,DESCRIPTION,UOM,
     * QTY ON ORD,RESTOCK LEVEL,REORDER POINT,QTY ON HAND,COST,DISCOUNT,BID,
     * EXTENDED COST,TAX CODE,PRICE
     *
     * <p>Required fields: CODE, QTY ON ORD, COST
     *
     * @param items       list of received cards
     * @param traderName  name of the trader/source
     * @param unitPrices  actual unit prices from the table (already condition-adjusted)
     * @param quantities  actual quantities from the table
     * @param paymentType payment type: {@code "credit"} (50%), {@code "check"} (33%),
     *                    {@code "partial"} (~41.67%), or {@code "inventory"} (0%)
     * @return the filename of the exported CSV
     */
    public String exportToPOSFormat(List<TradeItem> items, String traderName,
                                    List<BigDecimal> unitPrices, List<Integer> quantities,
                                    String paymentType) throws IOException {
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
            for (int i = 0; i < items.size(); i++) {
                TradeItem item = items.get(i);
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

                if (card.getFrameEffectDisplay() != null) {
                    descBuilder.append(" - ").append(card.getFrameEffects());
                }

                if (item.isFoil()) {
                    descBuilder.append(" (Foil)");
                }

                String description = escapeCSV(descBuilder.toString());

                // Use actual table values
                int qtyOnOrder = quantities.get(i);
                BigDecimal price = unitPrices.get(i); // Market price

                // Calculate cost based on payment type
                BigDecimal cost;
                if ("inventory".equalsIgnoreCase(paymentType)) {
                    cost = BigDecimal.ZERO; // Inventory = no cost
                } else if ("check".equalsIgnoreCase(paymentType)) {
                    cost = price.divide(CardConstants.PAYMENT_DIVISOR_CHECK, 2, java.math.RoundingMode.HALF_UP);
                } else if ("partial".equalsIgnoreCase(paymentType)) {
                    // Partial payment: weighted average of credit (50%) and check (33.33%) ≈ 41.67%
                    cost = price.multiply(CardConstants.PAYMENT_RATE_PARTIAL).setScale(2, java.math.RoundingMode.HALF_UP);
                } else {
                    // Default to store credit
                    cost = price.multiply(CardConstants.PAYMENT_RATE_CREDIT).setScale(2, java.math.RoundingMode.HALF_UP);
                }

                BigDecimal extendedCost = cost.multiply(BigDecimal.valueOf(qtyOnOrder));

                // LINE NO,DEPARTMENT,CATEGORY,TYPE,CODE,ITEM TYPE,ORDER NO,DESCRIPTION,UOM,
                // QTY ON ORD,RESTOCK LEVEL,REORDER POINT,QTY ON HAND,COST,DISCOUNT,BID,
                // EXTENDED COST,TAX CODE,PRICE
                writer.printf("%d,5,5.2,,%s,,%s,%s,,%d,,,%.2f,,,%.2f,TAX,%.2f%n",
                        lineNo++,               // LINE NO
                        code,                   // CODE (required)
                        "",                     // ORDER NO
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
     * Saves a human-readable card list for record keeping using table values.
     *
     * @param items          list of received cards
     * @param traderName     name of the trader/source
     * @param customerName   name of the customer
     * @param driversLicense driver's license number
     * @param checkNumber    check number (if applicable)
     * @param isStoreCredit  {@code true} for store credit (50%), {@code false} for check (33%)
     * @param conditions     list of condition strings for each card
     * @param unitPrices     actual unit prices from the table (already condition-adjusted)
     * @param quantities     actual quantities from the table
     * @return the filename of the saved list
     */
    public String saveCardList(List<TradeItem> items, String traderName, String customerName,
                               String driversLicense, String checkNumber, boolean isStoreCredit,
                               List<String> conditions, List<BigDecimal> unitPrices, List<Integer> quantities) throws IOException {
        ensureDataDirectoryExists();

        // New format: YYYY-MM-DD_HH-MM-SS_CUSTOMER_NAME
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String safeName = sanitizeFilename(customerName.isEmpty() ? "UNKNOWN" : customerName).toUpperCase();
        String filename = String.format("%s/%s_%s.txt",
                DATA_DIRECTORY, timestamp, safeName);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("╔════════════════════════════════════════════════════════════╗");
            writer.println("║          RECEIVED CARDS LIST - OCC CARD PRICER             ║");
            writer.println("╚════════════════════════════════════════════════════════════╝");
            writer.println();

            writer.println("Customer Name: " + customerName);
            writer.println("Trader Name: " + (traderName != null && !traderName.isEmpty() ? traderName : "N/A"));
            writer.println("Driver's License: " + (driversLicense != null && !driversLicense.isEmpty() ? driversLicense : "N/A"));
            writer.println("Payment Method: " + (isStoreCredit ? "Store Credit (50%)" : "Check (33%)"));
            if (!isStoreCredit && checkNumber != null && !checkNumber.isEmpty()) {
                writer.println("Check Number: " + checkNumber);
            }
            writer.println("Date: " + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            int totalCards = quantities.stream().mapToInt(Integer::intValue).sum();
            writer.println("Total Cards: " + totalCards);

            // Calculate total value from table values
            BigDecimal totalValue = BigDecimal.ZERO;
            for (int i = 0; i < items.size(); i++) {
                BigDecimal rowTotal = unitPrices.get(i).multiply(BigDecimal.valueOf(quantities.get(i)));
                totalValue = totalValue.add(rowTotal);
            }

            writer.printf("Total Value: $%.2f%n", totalValue);
            writer.printf("Half Rate (50%%): $%.2f%n",
                    totalValue.multiply(CardConstants.PAYMENT_RATE_CREDIT));
            writer.printf("Third Rate (33%%): $%.2f%n",
                    totalValue.multiply(CardConstants.PAYMENT_RATE_CHECK));

            writer.println("\n" + "=".repeat(70));
            writer.println("CARD LIST");
            writer.println("=".repeat(70));
            writer.printf("%-15s %-30s %-8s %-5s %-10s %-10s%n",
                    "Code", "Card Name", "Condition", "Qty", "Unit", "Total");
            writer.println("-".repeat(70));

            for (int i = 0; i < items.size(); i++) {
                TradeItem item = items.get(i);
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

                String condition = (conditions != null && i < conditions.size()) ? conditions.get(i) : "NM";
                int qty = quantities.get(i);
                BigDecimal unitPrice = unitPrices.get(i);
                BigDecimal rowTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

                writer.printf("%-15s %-30s %-8s %-5d $%-9.2f $%-9.2f%n",
                        codeBuilder.toString(),
                        truncate(nameBuilder.toString(), 30),
                        condition,
                        qty,
                        unitPrice,
                        rowTotal);
            }

            writer.println("-".repeat(70));
        }

        System.out.println("Card list saved: " + filename);
        return filename;
    }

    /**
     * Sanitizes a filename by removing invalid characters.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unknown";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }

    /**
     * Truncates a string to a maximum length.
     */
    private String truncate(String str, int length) {
        if (str == null || str.length() <= length) {
            return str;
        }
        return str.substring(0, length - 3) + "...";
    }

    /**
     * Escapes special characters in CSV values.
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        // Replace commas with ɕ to avoid CSV parsing issues
        // Replace quotes with double quotes for proper CSV escaping
        return value.replace(",", "ɕ").replace("\"", "\"\"");
    }

    /**
     * Quotes a string if it contains special characters.
     */
    private String quoteIfNeeded(String value) {
        if (value == null) {
            return "";
        }
        String escaped = escapeCSV(value);
        // Only quote if contains quotes (commas are already replaced)
        if (escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    /**
     * Exports trade items directly to inventory format (Item Wizard Change Qty).
     * Format: CODE,DESCRIPTION,EXTENDED DESCRIPTION,ON_HAND-QTY,NEW ON-HAND QTY
     */
    public String exportToInventoryFormat(List<TradeItem> items, List<String> conditions,
                                          List<Integer> quantities) throws IOException {
        ensureDataDirectoryExists();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("%s/inventory_from_trade_%s.csv", DATA_DIRECTORY, timestamp);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // No header for Item Wizard Change Qty format

            for (int i = 0; i < items.size(); i++) {
                TradeItem item = items.get(i);
                Card card = item.getCard();

                // Skip MISC cards
                if ("MISC".equals(card.getSetCode())) {
                    continue;
                }

                String code = card.getSetCode() + " " + card.getCollectorNumber();
                if (item.isFoil()) {
                    code += "F";
                }

                String cardName = card.getName();
                String artist = card.getArtist() != null ? card.getArtist() : "";
                int quantity = i < quantities.size() ? quantities.get(i) : 1;

                writer.println(formatChangeQtyRow(code, cardName, artist, quantity));
            }
        }

        return filename;
    }

    /**
     * Formats a row for Item Wizard Change Qty format.
     * Format: CODE,DESCRIPTION,EXTENDED DESCRIPTION,ON_HAND-QTY,NEW ON-HAND QTY
     */
    private String formatChangeQtyRow(String code, String cardName, String artist, int newQty) {
        // Replace commas with ɕ to avoid CSV parsing issues
        String escapedName = cardName.replace(",", "ɕ").replace("\"", "\"\"");
        String escapedArtist = artist.replace(",", "ɕ").replace("\"", "\"\"");

        // Format: CODE,DESCRIPTION,EXTENDED DESCRIPTION,ON_HAND-QTY,NEW ON-HAND QTY
        // We leave ON_HAND-QTY empty (they'll fill it in from current inventory)
        return String.format("%s,%s,%s,,%d",
                code,
                escapedName,
                escapedArtist,
                newQty);
    }
}
