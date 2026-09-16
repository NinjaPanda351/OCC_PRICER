package com.cardpricer.service;
import com.cardpricer.gui.panel.trade.TradeTableModel;
import com.cardpricer.gui.panel.trade.PaymentTypePanel;
import com.cardpricer.model.*;
import com.cardpricer.util.CardCodeParser;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowRegressionTest {
    @Test void parserDistinguishesNamesAndPreservesCollectorMarkers() {
        assertNull(CardCodeParser.parse("Black Lotus"));
        assertNull(CardCodeParser.parse("Lightning Bolt"));
        assertEquals("143b",CardCodeParser.parse("CHR143B").collectorNumber);
        assertEquals("73★",CardCodeParser.parse("TST 73★").collectorNumber);
        assertEquals("ARB-1",CardCodeParser.parse("PLST ARB 1").collectorNumber);
        assertEquals("E",CardCodeParser.parse("TST 1e").finish);
    }
    @Test void suffixVersionsAreComparedInOrder() {
        assertTrue(UpdateCheckService.isNewer("4.2.0","4.1.6-1"));
        assertTrue(UpdateCheckService.isNewer("4.1.6","4.1.6-1"));
        assertTrue(UpdateCheckService.isNewer("4.1.6-10","4.1.6-2"));
        assertFalse(UpdateCheckService.isNewer("4.1.5","4.1.6-1"));
    }
    @Test void typedRowsKeepConditionAndDuplicateBaseAfterSorting() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var model=new TradeTableModel(); Card card=new Card("Card","TST","73★");
            card.setRarity("rare");card.setEtchedPrice("10");
            var item=new TradeItem(card,true,1,"E"); model.addTrade(item,"NM",new BigDecimal("10.00"));
            model.setValueAt("LP",0,3);assertEquals(new BigDecimal("8.00"),model.priceAt(0));
            var copy=new TradeItem(item.getCard(),true,1,item.getFinishType());copy.setUnitPrice(item.getUnitPrice());
            model.addTrade(copy,"LP",model.priceAt(0));
            JTable table=new JTable(model);table.setAutoCreateRowSorter(true);table.getRowSorter().toggleSortOrder(1);
            int row=model.indexOf(copy.getLineId());model.setValueAt("NM",row,3);
            assertEquals(new BigDecimal("10.00"),model.priceAt(row));assertEquals("E",model.items().get(row).getFinishType());
            assertNotEquals(item.getLineId(),copy.getLineId());assertEquals(new BigDecimal("8.00"),model.priceAt(model.indexOf(item.getLineId())));
        });
    }
    @Test void splitInputsSurviveQuoteRefresh() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=new PaymentTypePanel(()->{});
            panel.restore("partial",new BigDecimal("25"),new BigDecimal("20"));
            panel.setTotal(new BigDecimal("100"),new BigDecimal("50"),new BigDecimal("40"));
            assertEquals(new BigDecimal("25"),panel.getPartialCreditPayout());assertEquals(new BigDecimal("20"),panel.getPartialCheckPayout());
            try {
                var labelField=PaymentTypePanel.class.getDeclaredField("partialTotalLabel");labelField.setAccessible(true);
                JLabel label=(JLabel)labelField.get(panel);assertFalse(label.getText().contains("Review"));
                panel.setTotal(new BigDecimal("200"),new BigDecimal("100"),new BigDecimal("80"));
                assertTrue(label.getText().contains("Review"));assertEquals(new BigDecimal("25"),panel.getPartialCreditPayout());
            }catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}
        });
    }
    @Test void manualOverrideDoesNotDestroyTheBaseAndSurvivesConditionRoundTrip() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            Card card=new Card("Card","TST","1");card.setPrice("10");card.setRarity("rare");
            var model=new TradeTableModel();var item=new TradeItem(card,false);model.addTrade(item,"NM",new BigDecimal("10.00"));
            model.setValueAt("LP",0,3);model.setValueAt("$7.25",0,5);model.setValueAt("NM",0,3);
            assertEquals(new BigDecimal("10.00"),model.priceAt(0));model.setValueAt("LP",0,3);
            assertEquals(new BigDecimal("7.25"),model.priceAt(0));assertEquals(new BigDecimal("10"),item.getUnitPrice());
        });
    }
    @Test void csvCostsReconcileWhenOneQuantityNeedsResidualCents() throws Exception {
        Card card=new Card("Card","TST","1");card.setPrice("1.00");
        var settlement=new SettlementEngine().settle(List.of(new SettlementEngine.Line("1",3,new BigDecimal("3.00"),
                new BigDecimal("1.50"),new BigDecimal("1.20"),true)),"partial",new BigDecimal("0.01"),new BigDecimal("1.19"));
        var writer=new java.io.StringWriter();TradePosEncoder.write(writer,List.of(new TradeItem(card,false,3)),List.of(new BigDecimal("1.00")),settlement);
        try (var parser=org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(new java.io.StringReader(writer.toString()))) {
            BigDecimal total=BigDecimal.ZERO;int quantity=0;
            for(var row:parser) { total=total.add(new BigDecimal(row.get("EXTENDED COST")));quantity+=Integer.parseInt(row.get("QTY ON ORD")); }
            assertEquals(settlement.total(),total);assertEquals(3,quantity);
        }
    }
    @Test void pdfCarriesExactUnicodeActualTextInEveryLocale() throws Exception {
        Locale previous=Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            String pdf=new String(ReceiptPrintService.createPdf("Éowyn Ω"),StandardCharsets.ISO_8859_1);
            assertTrue(pdf.startsWith("%PDF-1.4"));
            assertTrue(pdf.contains("FEFF"+java.util.HexFormat.of().formatHex("Éowyn Ω\n".getBytes(StandardCharsets.UTF_16BE))));
            assertTrue(pdf.contains("/ActualText"));
        } finally {Locale.setDefault(previous);}
    }
}
