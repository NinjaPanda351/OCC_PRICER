package com.cardpricer.service;

import com.cardpricer.model.Card;
import com.cardpricer.model.CardEntry;
import com.cardpricer.model.OrderItem;
import com.cardpricer.util.AppDataDirectory;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes catalog exports and invoices to UTF-8 CSV files. */
public class CsvExportService {
    private final Path dataDirectory;
    private final Path pricesDirectory;

    public enum ExportFormat {
        IMPORT_UTILITY, ITEM_WIZARD, ITEM_WIZARD_CHANGE_QTY_ZERO
    }

    public CsvExportService() {
        this(AppDataDirectory.root().toPath());
    }

    /** Explicit storage location, also allowing tests to avoid user data. */
    public CsvExportService(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.pricesDirectory = dataDirectory.resolve("prices");
    }

    public void exportCardsToCsv(List<Card> cards, String filename, ExportFormat format) throws IOException {
        Files.createDirectories(pricesDirectory);
        try (Writer writer = Files.newBufferedWriter(pricesDirectory.resolve(filename), StandardCharsets.UTF_8)) {
            CardCsvEncoder.write(writer, flattenCards(cards), format);
        }
    }

    public void exportCardsToCsv(List<Card> cards, String filename) throws IOException {
        exportCardsToCsv(cards, filename, ExportFormat.IMPORT_UTILITY);
    }

    public static List<CardEntry> flattenCards(List<Card> cards) {
        List<CardEntry> entries = new ArrayList<>();
        for (Card card : cards) {
            if (card.hasNormalPrice()) entries.add(new CardEntry(card, false));
            if (card.hasFoilPrice()) entries.add(new CardEntry(card, true));
            if (card.hasEtchedPrice()) entries.add(new CardEntry(card.getSetCode()+" "+card.getCollectorNumber()+"e",
                    card.getName(),card.getEtchedPriceAsBigDecimal(),card.getRarity(),card.getArtist()));
        }
        return entries;
    }

    public void exportInvoiceToCsv(List<OrderItem> items, String filename) throws IOException {
        Files.createDirectories(dataDirectory);
        try (Writer writer = Files.newBufferedWriter(dataDirectory.resolve(filename), StandardCharsets.UTF_8)) {
            writeInvoice(writer, items);
        }
    }

    public static void writeInvoice(Writer writer, List<OrderItem> items) throws IOException {
        CsvRows.write(writer, "Set Collector Code", "Card Name", "Finish", "Quantity", "Unit Price", "Total Price");
        BigDecimal grandTotal = BigDecimal.ZERO;
        int totalQuantity = 0;
        for (OrderItem item : items) {
            Card card = item.getCard();
            String code = card.getSetCode() + " " + card.getCollectorNumber() + (item.isFoil() ? "f" : "");
            CsvRows.write(writer, code, card.getName(), item.getFinish(), item.getQuantity(),
                    CsvRows.money(item.getUnitPrice()), CsvRows.money(item.getTotalPrice()));
            grandTotal = grandTotal.add(item.getTotalPrice());
            totalQuantity += item.getQuantity();
        }
        CsvRows.write(writer, "TOTAL", "", "", totalQuantity, "", CsvRows.money(grandTotal));
    }
}
