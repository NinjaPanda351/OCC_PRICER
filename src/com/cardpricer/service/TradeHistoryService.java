package com.cardpricer.service;

import com.cardpricer.gui.panel.PreferencesPanel;
import com.cardpricer.model.TradeRecord;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans {@code data/trades/*.txt} files and parses them into {@link TradeRecord} instances.
 * Also merges shared documents, keeping the latest revision of each trade.
 */
public class TradeHistoryService {

    private static final DateTimeFormatter FILENAME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final Pattern P_CUSTOMER = Pattern.compile("^Customer Name:\\s*(.+)$");
    private static final Pattern P_TRADER   = Pattern.compile("^Trader Name:\\s*(.+)$");
    private static final Pattern P_TOTAL_V  = Pattern.compile("^Total Value:\\s*\\$([\\d.,]+)$");
    private static final Pattern P_TOTAL_C  = Pattern.compile("^Total Cards:\\s*(\\d+)$");
    private static final Pattern P_PAYMENT  = Pattern.compile("^Payment Method:\\s*(.+)$");

    /**
     * Loads all trade records from {@code localDirectory} (and the shared folder if configured).
     * Results are sorted newest-first.
     *
     * @param localDirectory  path to the local trades directory (e.g. "data/trades")
     */
    public static List<TradeRecord> loadAll(String localDirectory) {
        return loadAll(localDirectory,PreferencesPanel.getSharedTradesFolder());
    }

    static List<TradeRecord> loadAll(String localDirectory, String sharedPath) {
        // Trade IDs combine revisions; legacy receipts use the filename as their identity.
        Map<String, TradeRecord> byFilename = new LinkedHashMap<>();

        loadFromDirectory(localDirectory, byFilename);
        try {
            var local=java.nio.file.Path.of(localDirectory);
            var repository=new TradeRepository(local.resolveSibling("ledger").resolve("trades.sqlite"));
            for (TradeRecord record:byFilename.values()) if (record.tradeId==null)
                repository.importLegacy(java.nio.file.Path.of(record.filename));
            for (TradeRecord record:repository.history(local)) byFilename.put(record.historyKey(),record);
        } catch (Exception failure) { System.err.println("Structured trade history unavailable: "+failure.getMessage()); }

        if (sharedPath != null && !sharedPath.isBlank()) {
            loadFromDirectory(sharedPath, byFilename);
        }

        List<TradeRecord> result = new ArrayList<>(byFilename.values());
        try { result = new ArrayList<>(repository(localDirectory).inventoryStatuses(result)); }
        catch (Exception failure) { throw new IllegalStateException("Could not load POS inventory status",failure); }
        result.sort((a, b) -> b.date.compareTo(a.date));
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void loadFromDirectory(String dirPath, Map<String, TradeRecord> out) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".txt"));
        if (files == null) return;

        for (File f : files) {
            TradeRecord record = parseRecord(f);
            if (record != null) {
                out.merge(record.historyKey(), record, (old, next) -> next.revision > old.revision ? next : old);
            }
        }
    }

    private static TradeRecord parseRecord(File file) {
        LocalDateTime date = parseDateFromFilename(file);
        String customerName  = "Unknown";
        String traderName    = "Unknown";
        String paymentMethod = "Unknown";
        BigDecimal totalValue = BigDecimal.ZERO;
        int totalCards = 0;
        UUID tradeId = null;
        long revision = 0;

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                Matcher m;
                if (line.startsWith("Trade ID: ")) {
                    try { tradeId=UUID.fromString(line.substring(10).trim()); } catch (IllegalArgumentException ignored) {}
                } else if (line.startsWith("Revision: ")) {
                    try { revision=Long.parseLong(line.substring(10).trim()); } catch (NumberFormatException ignored) {}
                } else if ((m = P_CUSTOMER.matcher(line)).matches()) {
                    customerName = m.group(1).trim();
                } else if ((m = P_TRADER.matcher(line)).matches()) {
                    traderName = m.group(1).trim();
                } else if ((m = P_TOTAL_V.matcher(line)).matches()) {
                    try {
                        totalValue = new BigDecimal(m.group(1).replace(",", "").trim());
                    } catch (NumberFormatException ignored) {}
                } else if ((m = P_TOTAL_C.matcher(line)).matches()) {
                    try {
                        totalCards = Integer.parseInt(m.group(1).trim());
                    } catch (NumberFormatException ignored) {}
                } else if ((m = P_PAYMENT.matcher(line)).matches()) {
                    paymentMethod = m.group(1).trim();
                }
            }
        } catch (IOException e) {
            // Fall back to filename-only record
        }

        // Early structured receipts omitted the revision in their text; companion JSON retained it.
        if (tradeId!=null && revision==0) {
            String name=file.getName();
            var snapshot=file.toPath().resolveSibling(name.substring(0,name.length()-4)+".json");
            try {
                var json=new org.json.JSONObject(Files.readString(snapshot,StandardCharsets.UTF_8));
                if (tradeId.toString().equals(json.getString("id"))) revision=json.getLong("revision");
            } catch (IOException | RuntimeException ignored) { /* Keep older receipts readable without JSON. */ }
        }

        return new TradeRecord(
                file.getAbsolutePath(),
                date,
                customerName,
                traderName,
                paymentMethod,
                totalValue,
                totalCards, tradeId, revision, false
        );
    }

    public static TradeRepository repository(String localDirectory) throws Exception {
        return new TradeRepository(java.nio.file.Path.of(localDirectory).resolveSibling("ledger").resolve("trades.sqlite"));
    }

    /** Fall back to the committed receipt when an output folder is temporarily unavailable. */
    public static String receiptContent(TradeRecord record, String localDirectory) throws Exception {
        if (record.tradeId != null) {
            var repo=repository(localDirectory);
            var draft=repo.committedDraft(record.tradeId);
            if (draft!=null && draft.revision()==record.revision) return repo.receiptContent(record.tradeId);
        }
        return Files.readString(java.nio.file.Path.of(record.filename),StandardCharsets.UTF_8);
    }

    /** A legacy receipt can be corrected without inventing the missing card snapshots. */
    public static void saveLegacyReceipt(TradeRecord record, String content, String localDirectory) throws Exception {
        if (record.tradeId != null) throw new IllegalArgumentException("Use the trade editor for a structured trade");
        if (content.isBlank()) throw new IllegalArgumentException("The receipt cannot be empty");
        var repo=repository(localDirectory);
        var source=java.nio.file.Path.of(record.filename);
        repo.importLegacy(source);
        var destination=java.nio.file.Path.of(localDirectory).resolve(source.getFileName());
        if (Files.exists(destination) && !Files.isSameFile(source,destination)) repo.importLegacy(destination);
        com.cardpricer.util.AtomicFiles.write(destination,content);
        repo.setInventoried(record,false);
        InventoryStatusSyncService.requestSync();
    }

    private static LocalDateTime parseDateFromFilename(File file) {
        String name = file.getName();
        // Expected prefix: yyyy-MM-dd_HH-mm-ss  (19 chars)
        if (name.length() >= 19) {
            try {
                return LocalDateTime.parse(name.substring(0, 19), FILENAME_FMT);
            } catch (Exception ignored) {}
        }
        // Fall back to last-modified timestamp
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
    }
}
