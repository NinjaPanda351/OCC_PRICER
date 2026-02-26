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
    public final String customerName;
    public final String traderName;
    public final String paymentMethod;
    public final BigDecimal totalValue;
    public final int totalCards;

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
