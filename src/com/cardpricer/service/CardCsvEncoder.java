package com.cardpricer.service;

import com.cardpricer.model.CardEntry;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Locale;

/** One encoder used by both individual-set and combined catalog exports. */
public final class CardCsvEncoder {
    private CardCsvEncoder() {}

    public static void write(Writer writer, List<CardEntry> entries, CsvExportService.ExportFormat format)
            throws IOException {
        if (format == CsvExportService.ExportFormat.IMPORT_UTILITY) {
            CsvRows.write(writer, "DEPARTMENT", "CATEGORY", "CODE", "DESCRIPTION", "EXTENDED DESCRIPTION",
                    "SUB DESCRIPTION", "TAX", "PRICE");
        }
        for (CardEntry entry : entries) {
            CsvRows.writeRow(writer, row(entry, format));
        }
    }

    public static String row(CardEntry entry, CsvExportService.ExportFormat format) {
        return switch (format) {
            case IMPORT_UTILITY -> CsvRows.row("5", "5.2", entry.getSetCollectorCode(), entry.getCardName(),
                    entry.getArtist(), rarity(entry.getRarity()), "TAX", CsvRows.money(entry.getRoundedPrice()));
            case ITEM_WIZARD -> CsvRows.row(entry.getSetCollectorCode(), entry.getCardName(), "", "0", "0.0",
                    "0", "0", "0", CsvRows.money(entry.getRoundedPrice()));
            case ITEM_WIZARD_CHANGE_QTY_ZERO -> CsvRows.row(entry.getSetCollectorCode(), entry.getCardName(),
                    entry.getArtist(), "", "0");
        };
    }

    private static String rarity(String rarity) {
        if (rarity == null) return "";
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "common" -> "C";
            case "uncommon" -> "U";
            case "rare" -> "R";
            case "mythic" -> "M";
            default -> rarity.toUpperCase(Locale.ROOT);
        };
    }
}
