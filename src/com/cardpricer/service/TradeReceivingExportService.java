package com.cardpricer.service;

import com.cardpricer.model.TradeItem;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Facade delegating to the four focused export services.
 * Retained so that {@link com.cardpricer.gui.panel.TradePanel} call sites need no changes.
 *
 * @see PosExportService
 * @see ReceiptWriterService
 * @see InventoryExportService
 * @see SharedFolderService
 */
public class TradeReceivingExportService {

    private final PosExportService       posExport       = new PosExportService();
    private final ReceiptWriterService   receiptWriter   = new ReceiptWriterService();
    private final InventoryExportService inventoryExport = new InventoryExportService();

    public String exportToPOSFormat(List<TradeItem> items, String traderName, String customerName,
                                    List<BigDecimal> unitPrices, List<Integer> quantities,
                                    String paymentType) throws IOException {
        return posExport.exportToPOSFormat(items, traderName, customerName,
                unitPrices, quantities, paymentType);
    }

    public String saveCardList(List<TradeItem> items, String traderName, String customerName,
                               String driversLicense, String checkNumber, String paymentType,
                               BigDecimal partialCreditAmount, BigDecimal partialCheckAmount,
                               List<String> conditions, List<BigDecimal> unitPrices, List<Integer> quantities,
                               BigDecimal tierCreditTotal, BigDecimal tierCheckTotal) throws IOException {
        return receiptWriter.saveCardList(items, traderName, customerName,
                driversLicense, checkNumber, paymentType,
                partialCreditAmount, partialCheckAmount,
                conditions, unitPrices, quantities,
                tierCreditTotal, tierCheckTotal);
    }

    public String exportToInventoryFormat(List<TradeItem> items, List<String> conditions,
                                          List<Integer> quantities) throws IOException {
        return inventoryExport.exportToInventoryFormat(items, conditions, quantities);
    }
}
