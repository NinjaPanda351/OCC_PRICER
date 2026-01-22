package com.cardpricer.service;

import com.cardpricer.model.Card;
import com.cardpricer.model.CardEntry;
import com.cardpricer.model.OrderItem;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles exporting card data and invoices to CSV format
 * All files are saved to the 'data' directory
 */
public class CsvExportService {

    private static final String DATA_DIRECTORY = "data";

    public enum ExportFormat {
        IMPORT_UTILITY,  // Full format with department, category, artist, rarity, tax
        ITEM_WIZARD,      // Simplified format with placeholder fields
        ITEM_WIZARD_CHANGE_QTY_ZERO,
        TRADE
    }

    /**
     * Ensures the data directory exists
     */
    private void ensureDataDirectoryExists() {
        java.io.File dataDir = new java.io.File(DATA_DIRECTORY);
        if (!dataDir.exists()) {
            dataDir.mkdir();
        }
    }

    /**
     * Exports cards to CSV in the specified format
     * Each card gets 1-2 rows (normal and foil if available)
     * Files are saved to the data directory
     *
     * @param cards List of cards to export
     * @param filename Output filename
     * @param format Export format (IMPORT_UTILITY or ITEM_WIZARD)
     * @throws IOException if file cannot be written
     */
    public void exportCardsToCsv(List<Card> cards, String filename, ExportFormat format) throws IOException {
        ensureDataDirectoryExists();
        String fullPath = DATA_DIRECTORY + "/" + filename;

        // Convert cards to flattened entries
        List<CardEntry> entries = flattenCards(cards);

        try (PrintWriter writer = new PrintWriter(new FileWriter(fullPath))) {
            // Write header based on format
            if (format == ExportFormat.IMPORT_UTILITY) {
                writer.println("DEPARTMENT,CATEGORY,CODE,DESCRIPTION,EXTENDED DESCRIPTION,SUB DESCRIPTION,TAX,PRICE");
            }
            // Item Wizard format has no header

            // Write each entry
            for (CardEntry entry : entries) {
                if (format == ExportFormat.IMPORT_UTILITY) {
                    writer.println(entry.toImportUtilityRow());
                } else {
                    writer.println(entry.toItemWizardRow());
                }
            }
        }

        System.out.println("Exported " + entries.size() + " card entries to " + fullPath);
    }

    /**
     * Exports cards to CSV using Import Utility format (default)
     * @param cards List of cards to export
     * @param filename Output filename
     * @throws IOException if file cannot be written
     */
    public void exportCardsToCsv(List<Card> cards, String filename) throws IOException {
        exportCardsToCsv(cards, filename, ExportFormat.IMPORT_UTILITY);
    }

    /**
     * Converts a list of Cards into flattened CardEntry objects
     * Each card becomes 1-2 entries depending on price availability
     *
     * @param cards List of cards to flatten
     * @return List of card entries ready for export
     */
    private List<CardEntry> flattenCards(List<Card> cards) {
        List<CardEntry> entries = new ArrayList<>();

        for (Card card : cards) {
            // Add normal version if it has a price
            if (card.hasNormalPrice()) {
                CardEntry normalEntry = new CardEntry(card, false);
                entries.add(normalEntry);
            }

            // Add foil version if it has a price
            if (card.hasFoilPrice()) {
                CardEntry foilEntry = new CardEntry(card, true);
                entries.add(foilEntry);
            }
        }

        return entries;
    }

    /**
     * Exports an order/invoice to CSV format
     * Files are saved to the data directory
     *
     * @param items List of order items
     * @param filename Output filename
     * @throws IOException if file cannot be written
     */
    public void exportInvoiceToCsv(List<OrderItem> items, String filename) throws IOException {
        ensureDataDirectoryExists();
        String fullPath = DATA_DIRECTORY + "/" + filename;

        try (PrintWriter writer = new PrintWriter(new FileWriter(fullPath))) {
            // Write header
            writer.println("Set Collector Code,Card Name,Finish,Quantity,Unit Price,Total Price");

            BigDecimal grandTotal = BigDecimal.ZERO;
            int totalQuantity = 0;

            // Write order items
            for (OrderItem item : items) {
                Card card = item.getCard();
                String setCollectorCode = card.getSetCode() + " " +
                        card.getCollectorNumber() +
                        (item.isFoil() ? "f" : "");

                writer.printf("\"%s\",\"%s\",%s,%d,%.2f,%.2f%n",
                        setCollectorCode,
                        escapeCSV(card.getName()),
                        item.getFinish(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalPrice());

                grandTotal = grandTotal.add(item.getTotalPrice());
                totalQuantity += item.getQuantity();
            }

            // Write total line
            writer.printf("TOTAL,,,,%d,,%.2f%n", totalQuantity, grandTotal);
        }

        System.out.println("Invoice exported to " + fullPath);
    }

    /**
     * Escapes special characters in CSV values
     * Handles quotes by doubling them
     *
     * @param value The string to escape
     * @return Escaped string safe for CSV
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\"\"");
    }
}