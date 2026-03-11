package com.cardpricer.service;

import com.cardpricer.model.Card;
import com.cardpricer.model.TradeItem;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes human-readable TXT trade receipts for record keeping.
 */
public final class ReceiptWriterService {

    private static final String DATA_DIRECTORY =
            com.cardpricer.util.AppDataDirectory.tradesPath();

    /**
     * Saves a human-readable card list/receipt.
     *
     * @param items               received cards
     * @param traderName          trader name
     * @param customerName        customer name
     * @param driversLicense      driver's license (may be null)
     * @param checkNumber         check number (may be null)
     * @param paymentType         "credit", "check", "partial", or "inventory"
     * @param partialCreditAmount store-credit payout for partial trades
     * @param partialCheckAmount  check payout for partial trades
     * @param conditions          condition string per item
     * @param unitPrices          condition-adjusted unit price per item
     * @param quantities          quantity per item
     * @param tierCreditTotal     tiered credit payout total
     * @param tierCheckTotal      tiered check payout total
     * @return absolute path of the generated file
     */
    public String saveCardList(List<TradeItem> items, String traderName, String customerName,
                               String driversLicense, String checkNumber, String paymentType,
                               BigDecimal partialCreditAmount, BigDecimal partialCheckAmount,
                               List<String> conditions, List<BigDecimal> unitPrices, List<Integer> quantities,
                               BigDecimal tierCreditTotal, BigDecimal tierCheckTotal) throws IOException {
        ensureDataDirExists();

        String timestamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String safeCustomer = PosExportService.sanitize(customerName.isEmpty() ? "UNKNOWN" : customerName).toUpperCase();
        String safeTrader   = PosExportService.sanitize(
                (traderName == null || traderName.isEmpty()) ? "UNKNOWN" : traderName).toUpperCase();
        String filename = String.format("%s/%s_%s_%s.txt", DATA_DIRECTORY, timestamp, safeCustomer, safeTrader);

        try (PrintWriter w = new PrintWriter(new FileWriter(filename))) {
            w.println("╔════════════════════════════════════════════════════════════╗");
            w.println("║          RECEIVED CARDS LIST - OCC CARD PRICER             ║");
            w.println("╚════════════════════════════════════════════════════════════╝");
            w.println();

            w.println("Customer Name: " + customerName);
            w.println("Trader Name: " + (traderName != null && !traderName.isEmpty() ? traderName : "N/A"));
            w.println("Driver's License: " + (driversLicense != null && !driversLicense.isEmpty() ? driversLicense : "N/A"));
            w.println("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            int totalCards = quantities.stream().mapToInt(Integer::intValue).sum();
            w.println("Total Cards: " + totalCards);

            BigDecimal totalValue = BigDecimal.ZERO;
            for (int i = 0; i < items.size(); i++) {
                totalValue = totalValue.add(unitPrices.get(i).multiply(BigDecimal.valueOf(quantities.get(i))));
            }
            w.printf("Total Value: $%.2f%n", totalValue);

            BigDecimal safeCredit = tierCreditTotal    != null ? tierCreditTotal    : BigDecimal.ZERO;
            BigDecimal safeCheck  = tierCheckTotal     != null ? tierCheckTotal     : BigDecimal.ZERO;
            switch (paymentType) {
                case "partial": {
                    BigDecimal creditAmt = partialCreditAmount != null ? partialCreditAmount : BigDecimal.ZERO;
                    BigDecimal checkAmt  = partialCheckAmount  != null ? partialCheckAmount  : BigDecimal.ZERO;
                    w.println("Payment Method: Partial (Split)");
                    w.printf("  Store Credit Payout : $%.2f%n", creditAmt);
                    w.printf("  Check Payout        : $%.2f%n", checkAmt);
                    w.printf("  Total Payout        : $%.2f%n", creditAmt.add(checkAmt));
                    if (checkNumber != null && !checkNumber.isEmpty())
                        w.println("  Check Number        : " + checkNumber);
                    break;
                }
                case "check":
                    w.println("Payment Method: Check");
                    w.printf("Payout: $%.2f%n", safeCheck);
                    if (checkNumber != null && !checkNumber.isEmpty())
                        w.println("Check Number: " + checkNumber);
                    break;
                case "inventory":
                    w.println("Payment Method: Inventory (No Payout)");
                    break;
                default: // credit
                    w.println("Payment Method: Store Credit");
                    w.printf("Payout: $%.2f%n", safeCredit);
            }

            w.println("\n" + "=".repeat(78));
            w.println("CARD LIST");
            w.println("=".repeat(78));
            w.printf("%-50s %-10s %-8s %-5s %-10s%n", "Card Name", "Unit Price", "Condition", "Qty", "Total");
            w.println("-".repeat(78));

            for (int i = 0; i < items.size(); i++) {
                TradeItem item = items.get(i);
                Card      card = item.getCard();

                StringBuilder name = new StringBuilder(card.getName());
                if (card.getFrameEffectDisplay() != null) name.append(" - ").append(card.getFrameEffects());
                if (item.isFoil()) name.append(" (").append(item.getFinish()).append(")");

                String condition = (conditions != null && i < conditions.size()) ? conditions.get(i) : "NM";
                int        qty       = quantities.get(i);
                BigDecimal unitPrice = unitPrices.get(i);
                w.printf("%-50s $%-9.2f %-8s %-5d $%-9.2f%n",
                        truncate(name.toString(), 50), unitPrice, condition, qty,
                        unitPrice.multiply(BigDecimal.valueOf(qty)));
            }
            w.println("-".repeat(78));
        }

        System.out.println("Card list saved: " + filename);
        SharedFolderService.copyToSharedFolder(filename);
        return filename;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static void ensureDataDirExists() {
        new java.io.File(DATA_DIRECTORY).mkdirs();
    }

    private static String truncate(String s, int len) {
        if (s == null || s.length() <= len) return s;
        return s.substring(0, len - 3) + "...";
    }
}
