package com.cardpricer.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable value class representing a parsed trade receipt file.
 */
public class TradeRecord {
    /** Absolute path to the source .txt file. */
    public final String filename;
    /** Date/time parsed from the filename prefix (yyyy-MM-dd_HH-mm-ss). */
    public final LocalDateTime date;
    /** Name of the customer as read from the receipt header. */
    public final String customerName;
    /** Name of the trader/employee as read from the receipt header. */
    public final String traderName;
    /** Payment method string as read from the receipt header (e.g. "Store Credit (50%)"). */
    public final String paymentMethod;
    /** Total market value of the trade as parsed from the receipt. */
    public final BigDecimal totalValue;
    /** Total number of cards in the trade as parsed from the receipt. */
    public final int totalCards;

    /**
     * Creates an immutable TradeRecord.
     *
     * @param filename      absolute path to the source {@code .txt} file
     * @param date          date/time parsed from the filename prefix
     * @param customerName  customer name from the receipt header
     * @param traderName    trader/employee name from the receipt header
     * @param paymentMethod payment method string from the receipt header
     * @param totalValue    total market value from the receipt
     * @param totalCards    total card count from the receipt
     */
    public TradeRecord(String filename, LocalDateTime date, String customerName,
                       String traderName, String paymentMethod,
                       BigDecimal totalValue, int totalCards) {
        this.filename      = filename;
        this.date          = date;
        this.customerName  = customerName;
        this.traderName    = traderName;
        this.paymentMethod = paymentMethod;
        this.totalValue    = totalValue;
        this.totalCards    = totalCards;
    }
}
