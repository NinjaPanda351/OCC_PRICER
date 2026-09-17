package com.cardpricer.service;

import com.cardpricer.model.Card;
import com.cardpricer.model.CardEntry;
import com.cardpricer.model.OrderItem;
import com.cardpricer.model.TradeItem;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CsvExportTest {
    @TempDir Path directory;

    private static List<CSVRecord> parse(String csv) throws IOException {
        try (var parser = CSVFormat.RFC4180.parse(new java.io.StringReader(csv))) {
            return parser.getRecords();
        }
    }

    private static Card card(String name, String artist) {
        Card card = new Card(name, "TST", "12");
        card.setArtist(artist);
        card.setRarity("rare");
        card.setPrice("12.34");
        card.setFoilPrice("20.00");
        return card;
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ordinary name", "Name, with comma", "Only \"quotes\"", "Line one\r\nLine two", "Éowyn 日本語"})
    void catalogFormatsPreserveText(String text) throws Exception {
        CardEntry entry = new CardEntry(text, text, new BigDecimal("12.34"), "rare", text);
        for (var format : CsvExportService.ExportFormat.values()) {
            var records = parse(CardCsvEncoder.row(entry, format));
            assertEquals(1, records.size());
            CSVRecord row = records.getFirst();
            int offset = format == CsvExportService.ExportFormat.IMPORT_UTILITY ? 2 : 0;
            assertEquals(text, row.get(offset));
            assertEquals(text, row.get(offset + 1));
            int expectedSize = switch (format) {
                case IMPORT_UTILITY -> 8;
                case ITEM_WIZARD -> 9;
                case ITEM_WIZARD_CHANGE_QTY_ZERO -> 5;
            };
            assertEquals(expectedSize, row.size());
            if (format != CsvExportService.ExportFormat.ITEM_WIZARD) assertEquals(text, row.get(offset + 2));
        }
    }

    @ParameterizedTest
    @EnumSource(CsvExportService.ExportFormat.class)
    void individualAndCombinedExportsHaveIdenticalBytes(CsvExportService.ExportFormat format) throws Exception {
        List<Card> cards = List.of(card("Name, \"quoted\"\n日本語", "Artist \"name\""));
        new CsvExportService(directory).exportCardsToCsv(cards, "one-set.csv", format);
        StringWriter combined = new StringWriter();
        CardCsvEncoder.write(combined, CsvExportService.flattenCards(cards), format);
        assertEquals(combined.toString(), Files.readString(directory.resolve("prices/one-set.csv")));
        var rows = parse(combined.toString());
        int firstData = format == CsvExportService.ExportFormat.IMPORT_UTILITY ? 1 : 0;
        assertEquals(2 + firstData, rows.size());
        if (format == CsvExportService.ExportFormat.ITEM_WIZARD_CHANGE_QTY_ZERO) {
            assertEquals(List.of("TST 12", cards.getFirst().getName(), cards.getFirst().getArtist(), "", "0"),
                    rows.getFirst().toList());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "de-DE", "fr-FR", "tr-TR"})
    void tradeColumnsAndNumbersAreStableAcrossLocales(String locale) throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag(locale));
            List<String> published = new ArrayList<>();
            var service = new TradeReceivingExportService(directory, published::add);
            Card card = card("Éowyn, \"Shieldmaiden\"\r\nSecond line", "Artist");
            String filename = service.exportToPOSFormat(List.of(new TradeItem(card, false)), "Trader", "Customer",
                    List.of(new BigDecimal("100.25")), List.of(3), "credit");
            var rows = parse(Files.readString(Path.of(filename)));
            assertEquals(2, rows.size());
            assertEquals(19, rows.getFirst().size());
            assertEquals(List.of("1", "5", "5.2", "", "TST 12", "", "", "Éowynɕ \"Shieldmaiden\"\r\nSecond line", "", "3", "", "", "",
                    "50.13", "", "", "150.39", "TAX", "100.25"), rows.get(1).toList());
            assertEquals("COST", rows.getFirst().get(13));
            assertEquals("EXTENDED COST", rows.getFirst().get(16));
            assertEquals(List.of(filename), published);

            StringWriter invoice = new StringWriter();
            CsvExportService.writeInvoice(invoice, List.of(new OrderItem(card, false, 2)));
            var invoiceRows = parse(invoice.toString());
            assertTrue(invoiceRows.stream().allMatch(row -> row.size() == 6));
            assertEquals("12.34", invoiceRows.get(1).get(4));
            assertEquals(List.of("TOTAL", "", "", "2", "", "24.68"), invoiceRows.get(2).toList());

            CardEntry entry = new CardEntry(card, false);
            assertEquals("12.00", parse(entry.toImportUtilityRow()).getFirst().get(7));
            assertEquals("12.00", parse(entry.toItemWizardRow()).getFirst().get(8));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void inventoryPreservesPunctuationAndExcludesMisc() throws Exception {
        Card card = card("Name, \"quotes\"\n日本語", "Artist, \"quoted\"");
        Card misc = new Card("Miscellaneous", "MISC", "1");
        var service = new TradeReceivingExportService(directory, ignored -> fail("Inventory must not publish a trade"));
        String path = service.exportToInventoryFormat(List.of(new TradeItem(card, false), new TradeItem(misc, false)),
                List.of("NM", "NM"), List.of(4, 2));
        var rows = parse(Files.readString(Path.of(path)));
        assertEquals(1, rows.size());
        assertEquals(List.of("TST 12", card.getName(), card.getArtist(), "", "4"), rows.getFirst().toList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tradeNamesUsePosCommaSubstituteWithoutRequiringQuotedFields(boolean foil) throws Exception {
        String originalName="Koma, World-Eater, Test";
        Card card=card(originalName,"Artist");
        var service=new TradeReceivingExportService(directory,ignored -> {});
        var item=new TradeItem(card,foil);
        String filename=service.exportToPOSFormat(List.of(item),"Trader","Customer",
                List.of(new BigDecimal("12.34")),List.of(2),"credit");
        String csv=Files.readString(Path.of(filename));
        // The POS splits on commas rather than honoring a quoted name.
        String[] fields=csv.split("\\r\\n")[1].split(",",-1);
        assertEquals(19,fields.length);
        assertEquals("Komaɕ World-Eaterɕ Test"+(foil ? " ("+item.getFinish()+")" : ""),fields[7]);
        assertFalse(csv.contains("\""));
        assertFalse(csv.contains("&#"));
        assertEquals("2",fields[9]);assertEquals("",fields[12]);
        assertEquals("6.17",fields[13]);assertEquals("12.34",fields[16]);
        assertEquals("TAX",fields[17]);assertEquals("12.34",fields[18]);
        assertEquals(originalName,card.getName());
    }

    @Test
    void invoiceFileHasSixColumnsAndTotalsInTheRightPositions() throws Exception {
        Card card = card("Name, \"quoted\"", "Artist");
        new CsvExportService(directory).exportInvoiceToCsv(List.of(new OrderItem(card, false, 2), new OrderItem(card, true, 3)), "invoice.csv");
        var rows = parse(Files.readString(directory.resolve("invoice.csv")));
        assertEquals(4, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.size() == 6));
        assertEquals(card.getName(), rows.get(1).get(1));
        assertEquals(List.of("TOTAL", "", "", "5", "", "84.68"), rows.getLast().toList());
    }

    @Test
    void writeFailuresPropagateAndPreventPublishing() throws Exception {
        Writer failing = new Writer() {
            public void write(char[] value, int offset, int count) throws IOException { throw new IOException("Disk full"); }
            public void flush() {}
            public void close() {}
        };
        Card card = card("Name", "Artist");
        for (var format : CsvExportService.ExportFormat.values()) {
            assertThrows(IOException.class, () -> CardCsvEncoder.write(failing, List.of(new CardEntry(card, false)), format));
        }
        assertThrows(IOException.class, () -> CsvExportService.writeInvoice(failing, List.of(new OrderItem(card, false))));
        Path fileInsteadOfDirectory = Files.writeString(directory.resolve("blocked"), "not a directory");
        var service = new TradeReceivingExportService(fileInsteadOfDirectory, ignored -> fail("Must not publish a failed export"));
        assertThrows(IOException.class, () -> service.exportToPOSFormat(List.of(new TradeItem(card, false)), "Trader", "Customer",
                List.of(BigDecimal.TEN), List.of(1), "credit"));
        assertThrows(IOException.class, () -> new CsvExportService(fileInsteadOfDirectory)
                .exportCardsToCsv(List.of(card), "blocked.csv"));
    }

    @Test
    void zeroQuantityDoesNotRequireAPriceAndNullArtistIsEmpty() throws Exception {
        CardEntry entry = new CardEntry("TST 12", "Name", null, null, null);
        assertEquals(List.of("TST 12", "Name", "", "", "0"), parse(entry.toZeroOutItems()).getFirst().toList());
    }

    @Test
    void foilDetectionMatchesGeneratedCodesAndAcceptsLegacyUppercase() {
        assertTrue(new CardEntry(card("Name", "Artist"), true).isFoil());
        assertFalse(new CardEntry(card("Name", "Artist"), false).isFoil());
        assertTrue(new CardEntry("TST 12F", "Name", BigDecimal.ONE, "rare", "").isFoil());
    }
}
