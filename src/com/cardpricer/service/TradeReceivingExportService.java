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

    // Condition multipliers (NM = 1.0, each tier = 0.8 of previous)
    private static final String[] CONDITIONS = {"NM", "LP", "MP", "HP", "DMG"};
    private static final double[] CONDITION_MULTIPLIERS = {
            1.0,    // NM - Market Price
            0.8,    // LP - 80% of NM
            0.64,   // MP - 80% of LP
            0.512,  // HP - 80% of MP
            0.4096  // DMG - 80% of HP
    };

    private void ensureDataDirectoryExists() {
        java.io.File dataDir = new java.io.File(DATA_DIRECTORY);
        if (!dataDir.exists()) {
            dataDir.mkdirs(); // Use mkdirs() to create parent directories too
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
    /**
     * Exports received cards to POS inventory import CSV format using table values
     * Format: LINE NO,DEPARTMENT,CATEGORY,TYPE,CODE,ITEM TYPE,ORDER NO,DESCRIPTION,UOM,
     *         QTY ON ORD,RESTOCK LEVEL,REORDER POINT,QTY ON HAND,COST,DISCOUNT,BID,
     *         EXTENDED COST,TAX CODE,PRICE
     *
     * Required fields: CODE, QTY ON ORD, COST
     *
     * @param items List of received cards
     * @param traderName Name of trader/source
     * @param unitPrices Actual unit prices from table (already condition-adjusted)
     * @param quantities Actual quantities from table
     * @param paymentType Payment type: "credit" (50%), "check" (33%), or "inventory" (0%)
     * @return The filename of the exported CSV
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
                    cost = price.multiply(new BigDecimal("0.33")).setScale(2, java.math.RoundingMode.HALF_UP); // 33%
                } else {
                    // Default to store credit
                    cost = price.multiply(new BigDecimal("0.50")).setScale(2, java.math.RoundingMode.HALF_UP); // 50%
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
     * Legacy method - delegates to new method with recalculated values
     * @deprecated Use exportToPOSFormat with unitPrices and quantities parameters
     */
    @Deprecated
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
     * @param customerName Name of customer
     * @param driversLicense Driver's license number
     * @param checkNumber Check number (if applicable)
     * @param isStoreCredit Whether payment is store credit (true) or check (false)
     * @param conditions List of conditions for each card
     * @return The filename of the saved list
     */
    /**
     * Saves a human-readable card list for record keeping using table values
     *
     * @param items List of received cards
     * @param traderName Name of trader/source
     * @param customerName Name of customer
     * @param driversLicense Driver's license number
     * @param checkNumber Check number (if applicable)
     * @param isStoreCredit Whether payment is store credit (true) or check (false)
     * @param conditions List of conditions for each card
     * @param unitPrices Actual unit prices from table (already condition-adjusted)
     * @param quantities Actual quantities from table
     * @return The filename of the saved list
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
                    totalValue.multiply(new BigDecimal("0.50")));
            writer.printf("Third Rate (33%%): $%.2f%n",
                    totalValue.multiply(new BigDecimal("0.33")));

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
     * Legacy method - delegates to new method with recalculated values
     * @deprecated Use saveCardList with unitPrices and quantities parameters
     */
    @Deprecated
    public String saveCardList(List<TradeItem> items, String traderName, String customerName,
                               String driversLicense, String checkNumber, boolean isStoreCredit,
                               List<String> conditions) throws IOException {
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
            writer.println("Total Cards: " + items.size());

            BigDecimal totalValue = BigDecimal.ZERO;
            for (int i = 0; i < items.size(); i++) {
                TradeItem item = items.get(i);
                Card card = item.getCard();
                BigDecimal roundedPrice = applyPricingRules(item.getUnitPrice(), card.getRarity());

                // Apply condition multiplier if conditions list is provided
                if (conditions != null && i < conditions.size()) {
                    String condition = conditions.get(i);
                    roundedPrice = applyConditionMultiplier(roundedPrice, condition);
                }

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
            writer.printf("%-15s %-35s %-8s %-10s%n", "Code", "Card Name", "Condition", "Price");
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

                // Apply pricing rules
                BigDecimal roundedPrice = applyPricingRules(item.getUnitPrice(), card.getRarity());

                // Get condition and apply multiplier
                String condition = "NM";
                if (conditions != null && i < conditions.size()) {
                    condition = conditions.get(i);
                    roundedPrice = applyConditionMultiplier(roundedPrice, condition);
                }

                writer.printf("%-15s %-35s %-8s $%-9.2f%n",
                        codeBuilder.toString(),
                        truncate(nameBuilder.toString(), 35),
                        condition,
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
        // Replace commas with ɕ to avoid CSV parsing issues
        // Replace quotes with double quotes for proper CSV escaping
        return value.replace(",", "ɕ").replace("\"", "\"\"");
    }

    /**
     * Quotes a string if it contains special characters (no longer needed for commas since we replace them)
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
     * Applies condition multiplier to a price and re-applies rounding rules
     */
    private BigDecimal applyConditionMultiplier(BigDecimal basePrice, String condition) {
        int conditionIndex = getConditionIndex(condition);
        double multiplier = CONDITION_MULTIPLIERS[conditionIndex];

        BigDecimal adjustedPrice = basePrice.multiply(BigDecimal.valueOf(multiplier));

        // Re-apply rounding rules to the condition-adjusted price
        if (adjustedPrice.compareTo(BigDecimal.TEN) < 0) {
            // Below $10 - round to nearest $0.50
            BigDecimal rounded = adjustedPrice.divide(new BigDecimal("0.5"), 0, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("0.5"));
            return rounded;
        } else {
            // $10 and above - round to nearest $1.00
            return adjustedPrice.setScale(0, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Gets the index of a condition in the CONDITIONS array
     */
    private int getConditionIndex(String condition) {
        for (int i = 0; i < CONDITIONS.length; i++) {
            if (CONDITIONS[i].equals(condition)) {
                return i;
            }
        }
        return 0; // Default to NM
    }

    /**
     * Exports trade items directly to inventory format (Item Wizard Change Qty)
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
     * Formats a row for Item Wizard Change Qty format
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