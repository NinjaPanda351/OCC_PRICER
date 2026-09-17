package com.cardpricer.service;


import com.cardpricer.model.Card;
import com.cardpricer.model.TradeItem;
import com.cardpricer.util.CardConstants;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
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

    private final Path dataDirectory;
    private final Consumer<String> sharedPublisher;

    public TradeReceivingExportService() {
        this(com.cardpricer.util.AppDataDirectory.trades().toPath(),
                TradeReceivingExportService::copyToSharedFolder);
    }

    /** Explicit destinations keep file generation independent of workstation preferences. */
    public TradeReceivingExportService(Path dataDirectory, Consumer<String> sharedPublisher) {
        this.dataDirectory = java.util.Objects.requireNonNull(dataDirectory);
        this.sharedPublisher = java.util.Objects.requireNonNull(sharedPublisher);
    }

    private void ensureDataDirectoryExists() throws IOException {
        Files.createDirectories(dataDirectory);
    }

    // Dedicated daemon thread for all shared-folder I/O so it never blocks the EDT.
    private static final java.util.concurrent.ExecutorService SHARED_FOLDER_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "shared-folder-copy");
                t.setDaemon(true);
                return t;
            });

    /**
     * Copies {@code localFilePath} to the shared trades folder asynchronously.
     * Returns immediately — the caller (EDT) is never blocked.  The copy is
     * best-effort; all failures are logged but never propagated.
     */
    private static void copyToSharedFolder(String localFilePath) { syncMissingToSharedFolder(); }

    /**
     * Queues a background sync of any local trade files not yet present (or smaller than
     * the local copy) in the shared folder.  Returns immediately — safe to call on the EDT
     * at startup.  Useful for catching files that were missed when the network was down.
     */
    public static void syncMissingToSharedFolder() { syncMissingToSharedFolder(false); }
    private static final java.util.concurrent.atomic.AtomicBoolean SYNC_RUNNING = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean FORCE_RETRY = new java.util.concurrent.atomic.AtomicBoolean();
    public static void syncMissingToSharedFolder(boolean force) {
        InventoryStatusSyncService.requestSync();
        String shared=java.util.prefs.Preferences.userRoot().node("/com/cardpricer/gui/panel").get("shared.trades.folder", "");
        if (shared.isBlank()) { sharedSyncStatus="saved locally"; return; }
        if (force) FORCE_RETRY.set(true);
        if (!SYNC_RUNNING.compareAndSet(false,true)) return;
        sharedSyncStatus="pending sync";
        SHARED_FOLDER_EXECUTOR.submit(() -> {
            try {
                Path local=com.cardpricer.util.AppDataDirectory.trades().toPath();
                var sync=new SharedFolderSyncService(com.cardpricer.util.AppDataDirectory.root().toPath().resolve("ledger/trades.sqlite"));
                try (var files=Files.list(local)) {
                    for (Path file:files.filter(Files::isRegularFile).toList())
                        sync.enqueue(file,Path.of(shared).resolve(file.getFileName()));
                }
                int pending=sync.retry(FORCE_RETRY.getAndSet(false));
                pending+=SharedTradeService.retrySharedOutputs(Path.of(shared));
                sharedSyncStatus=pending==0 ? "synced" : pending+" output(s) pending sync or in conflict";
            } catch (Exception e) { sharedSyncStatus="pending sync: "+e.getMessage(); }
            finally { SYNC_RUNNING.set(false); }
        });
    }
    private static volatile String sharedSyncStatus="saved locally";
    public static String getSharedSyncStatus() { return sharedSyncStatus; }

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
     * @param paymentType payment type: {@code "credit"} (50%), {@code "check"} (40%),
     *                    {@code "partial"} (requires approved settlement), or {@code "inventory"} (0%)
     * @return the filename of the exported CSV
     */
    public String exportToPOSFormat(List<TradeItem> items, String traderName, String customerName,
                                    List<BigDecimal> unitPrices, List<Integer> quantities,
                                    String paymentType) throws IOException {
        if ("partial".equals(paymentType))
            throw new IllegalArgumentException("A split export requires an approved settlement");
        List<SettlementEngine.Line> lines = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            BigDecimal qty = BigDecimal.valueOf(quantities.get(i));
            BigDecimal price = unitPrices.get(i);
            lines.add(new SettlementEngine.Line(Integer.toString(i), quantities.get(i), price.multiply(qty),
                    price.multiply(new BigDecimal("0.50")).setScale(2, java.math.RoundingMode.HALF_UP).multiply(qty),
                    price.multiply(new BigDecimal("0.40")).setScale(2, java.math.RoundingMode.HALF_UP).multiply(qty),
                    !"MISC".equalsIgnoreCase(items.get(i).getCard().getSetCode())));
        }
        return exportToPOSFormat(items, traderName, customerName, unitPrices,
                new SettlementEngine().settle(lines, paymentType, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    public String exportToPOSFormat(List<TradeItem> items, String traderName, String customerName,
                                    List<BigDecimal> unitPrices, SettlementEngine.Settlement settlement) throws IOException {
        java.io.StringWriter content = new java.io.StringWriter();
        TradePosEncoder.write(content, items, unitPrices, settlement);
        ensureDataDirectoryExists();
        Path file = dataDirectory.resolve("trade_" + java.util.UUID.randomUUID() + ".csv");
        Files.writeString(file, content.toString(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        sharedPublisher.accept(file.toString());
        return file.toString();
    }

    /**
     * Saves a human-readable card list for record keeping using table values.
     *
     * @param items                list of received cards
     * @param traderName           name of the trader/source
     * @param customerName         name of the customer
     * @param driversLicense       driver's license number
     * @param checkNumber          check number (if applicable)
     * @param paymentType          "credit", "check", "partial", or "inventory"
     * @param partialCreditAmount  store-credit payout for partial trades (ignored otherwise)
     * @param partialCheckAmount   check payout for partial trades (ignored otherwise)
     * @param conditions           list of condition strings for each card
     * @param unitPrices           actual unit prices from the table (already condition-adjusted)
     * @param quantities           actual quantities from the table
     * @param tierCreditTotal      accumulated tiered credit payout (used for credit/check receipt lines)
     * @param tierCheckTotal       accumulated tiered check payout (used for credit/check receipt lines)
     * @return the filename of the saved list
     */
    public String saveCardList(List<TradeItem> items, String traderName, String customerName,
                               String driversLicense, String checkNumber, String paymentType,
                               BigDecimal partialCreditAmount, BigDecimal partialCheckAmount,
                               List<String> conditions, List<BigDecimal> unitPrices, List<Integer> quantities,
                               BigDecimal tierCreditTotal, BigDecimal tierCheckTotal) throws IOException {
        ensureDataDirectoryExists();

        String filename = dataDirectory.resolve(TradeApplicationService.filenamePrefix(
                LocalDateTime.now(),customerName,traderName,java.util.UUID.randomUUID()) + ".txt").toString();

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Path.of(filename), StandardCharsets.UTF_8))) {
            writer.println("╔════════════════════════════════════════════════════════════╗");
            writer.println("║          RECEIVED CARDS LIST - OCC CARD PRICER             ║");
            writer.println("╚════════════════════════════════════════════════════════════╝");
            writer.println();

            writer.println("Customer Name: " + customerName);
            writer.println("Trader Name: " + (traderName != null && !traderName.isEmpty() ? traderName : "N/A"));
            writer.println("Driver's License: " + (driversLicense != null && !driversLicense.isEmpty() ? driversLicense : "N/A"));
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

            writer.printf(Locale.ROOT, "Total Value: $%.2f%n", totalValue);

            // Payment method and payout breakdown
            BigDecimal safeCredit = tierCreditTotal != null ? tierCreditTotal : BigDecimal.ZERO;
            BigDecimal safeCheck  = tierCheckTotal  != null ? tierCheckTotal  : BigDecimal.ZERO;
            switch (paymentType) {
                case "partial":
                    BigDecimal safeCreditAmt = partialCreditAmount != null ? partialCreditAmount : BigDecimal.ZERO;
                    BigDecimal safeCheckAmt  = partialCheckAmount  != null ? partialCheckAmount  : BigDecimal.ZERO;
                    writer.println("Payment Method: Partial (Split)");
                    writer.printf(Locale.ROOT, "  Store Credit Payout : $%.2f%n", safeCreditAmt);
                    writer.printf(Locale.ROOT, "  Check Payout        : $%.2f%n", safeCheckAmt);
                    writer.printf(Locale.ROOT, "  Total Payout        : $%.2f%n", safeCreditAmt.add(safeCheckAmt));
                    if (checkNumber != null && !checkNumber.isEmpty()) {
                        writer.println("  Check Number        : " + checkNumber);
                    }
                    break;
                case "check":
                    writer.println("Payment Method: Check");
                    writer.printf(Locale.ROOT, "Payout: $%.2f%n", safeCheck);
                    if (checkNumber != null && !checkNumber.isEmpty()) {
                        writer.println("Check Number: " + checkNumber);
                    }
                    break;
                case "inventory":
                    writer.println("Payment Method: Inventory (No Payout)");
                    break;
                default: // "credit"
                    writer.println("Payment Method: Store Credit");
                    writer.printf(Locale.ROOT, "Payout: $%.2f%n", safeCredit);
                    break;
            }

            writer.println("\n" + "=".repeat(78));
            writer.println("CARD LIST");
            writer.println("=".repeat(78));
            writer.printf(Locale.ROOT, "%-50s %-10s %-8s %-5s %-10s%n",
                    "Card Name", "Unit Price", "Condition", "Qty", "Total");
            writer.println("-".repeat(78));

            for (int i = 0; i < items.size(); i++) {
                TradeItem item = items.get(i);
                Card card = item.getCard();

                StringBuilder nameBuilder = new StringBuilder();
                nameBuilder.append(card.getName());
                if (item.isFoil()) {
                    nameBuilder.append(" (").append(item.getFinish()).append(")");
                }

                String condition = (conditions != null && i < conditions.size()) ? conditions.get(i) : "NM";
                int qty = quantities.get(i);
                BigDecimal unitPrice = unitPrices.get(i);
                BigDecimal rowTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

                writer.printf(Locale.ROOT, "%-50s $%-9.2f %-8s %-5d $%-9.2f%n",
                        nameBuilder + " [" + item.getSetCollectorCode() + "]",
                        unitPrice,
                        condition,
                        qty,
                        rowTotal);
            }

            writer.println("-".repeat(78));
            if (writer.checkError()) throw new IOException("Could not write receipt: " + filename);
        }

        System.out.println("Card list saved: " + filename);
        sharedPublisher.accept(filename);
        return filename;
    }

    /**
     * Sanitizes a filename by removing invalid characters.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unknown";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase(Locale.ROOT);
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
     * Exports trade items to Item Wizard Change Qty inventory format.
     *
     * <p>Format: {@code CODE,DESCRIPTION,EXTENDED DESCRIPTION,ON_HAND-QTY,NEW ON-HAND QTY}
     *
     * <p>Cards with set code {@code "MISC"} are excluded from the output.
     *
     * @param items      list of received trade items
     * @param conditions condition string for each item (may be {@code null})
     * @param quantities quantity for each item
     * @return the absolute path of the generated CSV file
     * @throws IOException if the file cannot be written
     */
    public String exportToInventoryFormat(List<TradeItem> items, List<String> conditions,
                                          List<Integer> quantities) throws IOException {
        ensureDataDirectoryExists();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_" + java.util.UUID.randomUUID();
        String filename = String.format("%s/inventory_from_trade_%s.csv", dataDirectory, timestamp);

        try (Writer writer = Files.newBufferedWriter(Path.of(filename), StandardCharsets.UTF_8)) {
            // No header for Item Wizard Change Qty format

            for (int i = 0; i < items.size(); i++) {
                TradeItem item = items.get(i);
                Card card = item.getCard();

                // Skip MISC cards
                if ("MISC".equals(card.getSetCode())) {
                    continue;
                }

                String cn = card.getCollectorNumber();
                String code = card.getSetCode().equalsIgnoreCase("plst") ? cn.replace('-', ' ')
                        : card.getSetCode() + " " + cn;
                if (item.isFoil()) {
                    code += item.getFinishType();
                }

                String cardName = card.getName();
                String artist = card.getArtist() != null ? card.getArtist() : "";
                int quantity = i < quantities.size() ? quantities.get(i) : 1;

                CsvRows.write(writer, code, cardName, artist, "", quantity);
            }
        }

        return filename;
    }

}
