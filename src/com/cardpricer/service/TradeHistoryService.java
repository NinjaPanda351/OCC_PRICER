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
 * Also merges records from the shared folder (if configured), deduplicating by filename.
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
        // Use a LinkedHashMap keyed by base filename to deduplicate between local & shared
        Map<String, TradeRecord> byFilename = new LinkedHashMap<>();

        loadFromDirectory(localDirectory, byFilename);

        String sharedPath = PreferencesPanel.getSharedTradesFolder();
        if (sharedPath != null && !sharedPath.isBlank()) {
            loadFromDirectory(sharedPath, byFilename);
        }

        List<TradeRecord> result = new ArrayList<>(byFilename.values());
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
                out.putIfAbsent(f.getName(), record);
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

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                Matcher m;
                if ((m = P_CUSTOMER.matcher(line)).matches()) {
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

        return new TradeRecord(
                file.getAbsolutePath(),
                date,
                customerName,
                traderName,
                paymentMethod,
                totalValue,
                totalCards
        );
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
