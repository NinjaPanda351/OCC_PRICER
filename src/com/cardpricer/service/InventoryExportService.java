package com.cardpricer.service;

import com.cardpricer.model.Card;
import com.cardpricer.model.TradeItem;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports trade items to Item Wizard Change Qty inventory CSV format.
 *
 * <p>Output format: {@code CODE,DESCRIPTION,EXTENDED DESCRIPTION,ON_HAND-QTY,NEW ON-HAND QTY}
 *
 * <p>Cards with set code {@code "MISC"} are excluded.
 */
public final class InventoryExportService {

    private static final String DATA_DIRECTORY =
            com.cardpricer.util.AppDataDirectory.tradesPath();

    /**
     * @param items      received trade items
     * @param conditions condition per item (may be null)
     * @param quantities quantity per item
     * @return absolute path of the generated CSV
     */
    public String exportToInventoryFormat(List<TradeItem> items, List<String> conditions,
                                          List<Integer> quantities) throws IOException {
        ensureDataDirExists();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename  = String.format("%s/inventory_from_trade_%s.csv", DATA_DIRECTORY, timestamp);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            for (int i = 0; i < items.size(); i++) {
                TradeItem item = items.get(i);
                Card      card = item.getCard();
                if ("MISC".equals(card.getSetCode())) continue;

                String code = card.getSetCode() + " " + card.getCollectorNumber()
                        + (item.isFoil() ? "F" : "");
                String artist = card.getArtist() != null ? card.getArtist() : "";
                int qty = i < quantities.size() ? quantities.get(i) : 1;

                writer.println(formatChangeQtyRow(code, card.getName(), artist, qty));
            }
        }

        return filename;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static void ensureDataDirExists() {
        new java.io.File(DATA_DIRECTORY).mkdirs();
    }

    /** Format: CODE,DESCRIPTION,EXTENDED DESCRIPTION,ON_HAND-QTY,NEW ON-HAND QTY */
    private static String formatChangeQtyRow(String code, String cardName, String artist, int newQty) {
        return String.format("%s,%s,%s,,%d",
                code,
                cardName.replace(",", "ɕ").replace("\"", "\"\""),
                artist.replace(",", "ɕ").replace("\"", "\"\""),
                newQty);
    }
}
