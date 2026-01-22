package com.cardpricer.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete trade transaction between two parties
 */
public class Trade {
    private String myTraderName;
    private String myTradeDate;
    private String myNotes;
    private double myTradeRate; // Percentage (100.0 = 100%)
    private List<TradeItem> myGivingItems;
    private List<TradeItem> myReceivingItems;
    private String myTradeId;

    /**
     * Creates a new empty trade
     */
    public Trade() {
        this.myGivingItems = new ArrayList<>();
        this.myReceivingItems = new ArrayList<>();
        this.myTradeRate = 100.0; // Default to 100%
        this.myTradeDate = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        this.myTradeId = generateTradeId();
    }

    /**
     * Creates a trade with trader information
     */
    public Trade(final String theTraderName, final String theTradeDate) {
        this();
        this.myTraderName = theTraderName;
        this.myTradeDate = theTradeDate;
    }

    // Getters and Setters
    public String getTraderName() {
        return myTraderName;
    }

    public void setTraderName(final String theTraderName) {
        this.myTraderName = theTraderName;
    }

    public String getTradeDate() {
        return myTradeDate;
    }

    public void setTradeDate(final String theTradeDate) {
        this.myTradeDate = theTradeDate;
    }

    public String getNotes() {
        return myNotes;
    }

    public void setNotes(final String theNotes) {
        this.myNotes = theNotes;
    }

    public double getTradeRate() {
        return myTradeRate;
    }

    public void setTradeRate(final double theTradeRate) {
        if (theTradeRate < 0) {
            throw new IllegalArgumentException("Trade rate cannot be negative");
        }
        this.myTradeRate = theTradeRate;
    }

    public String getTradeId() {
        return myTradeId;
    }

    public List<TradeItem> getGivingItems() {
        return new ArrayList<>(myGivingItems);
    }

    public List<TradeItem> getReceivingItems() {
        return new ArrayList<>(myReceivingItems);
    }

    // Item management
    public void addGivingItem(final TradeItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Trade item cannot be null");
        }
        myGivingItems.add(item);
    }

    public void addReceivingItem(final TradeItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Trade item cannot be null");
        }
        myReceivingItems.add(item);
    }

    public void removeGivingItem(final int index) {
        if (index >= 0 && index < myGivingItems.size()) {
            myGivingItems.remove(index);
        }
    }

    public void removeReceivingItem(final int index) {
        if (index >= 0 && index < myReceivingItems.size()) {
            myReceivingItems.remove(index);
        }
    }

    public void clearGivingItems() {
        myGivingItems.clear();
    }

    public void clearReceivingItems() {
        myReceivingItems.clear();
    }

    // Calculations
    /**
     * Calculates the total value of cards being given away
     */
    public BigDecimal getGivingTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (TradeItem item : myGivingItems) {
            total = total.add(item.getTotalPrice());
        }
        return total;
    }

    /**
     * Calculates the total value of cards being received
     */
    public BigDecimal getReceivingTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (TradeItem item : myReceivingItems) {
            total = total.add(item.getTotalPrice());
        }
        return total;
    }

    /**
     * Calculates the adjusted receiving total based on trade rate
     * For example, if trade rate is 80%, receiving $100 worth = $80 effective value
     */
    public BigDecimal getAdjustedReceivingTotal() {
        BigDecimal total = getReceivingTotal();
        BigDecimal rate = BigDecimal.valueOf(myTradeRate / 100.0);
        return total.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calculates the difference (positive = receiving more, negative = giving more)
     */
    public BigDecimal getDifference() {
        return getReceivingTotal().subtract(getGivingTotal());
    }

    /**
     * Calculates the adjusted difference based on trade rate
     */
    public BigDecimal getAdjustedDifference() {
        return getAdjustedReceivingTotal().subtract(getGivingTotal());
    }

    /**
     * Checks if the trade is balanced (within $1.00)
     */
    public boolean isBalanced() {
        return getDifference().abs().compareTo(BigDecimal.ONE) <= 0;
    }

    /**
     * Checks if the adjusted trade is balanced (within $1.00)
     */
    public boolean isAdjustedBalanced() {
        return getAdjustedDifference().abs().compareTo(BigDecimal.ONE) <= 0;
    }

    /**
     * Gets total number of cards in the trade
     */
    public int getTotalCardCount() {
        int count = 0;
        for (TradeItem item : myGivingItems) {
            count += item.getQuantity();
        }
        for (TradeItem item : myReceivingItems) {
            count += item.getQuantity();
        }
        return count;
    }

    /**
     * Generates a unique trade ID based on timestamp
     */
    private String generateTradeId() {
        return "TRADE_" + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    @Override
    public String toString() {
        return String.format("Trade with %s on %s - Giving: $%.2f, Receiving: $%.2f, Difference: $%.2f",
                myTraderName != null ? myTraderName : "Unknown",
                myTradeDate,
                getGivingTotal(),
                getReceivingTotal(),
                getDifference());
    }

    /**
     * Returns a detailed summary of the trade
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TRADE SUMMARY ===\n");
        sb.append("Trader: ").append(myTraderName).append("\n");
        sb.append("Date: ").append(myTradeDate).append("\n");
        sb.append("Trade ID: ").append(myTradeId).append("\n");
        sb.append("Trade Rate: ").append(String.format("%.1f%%", myTradeRate)).append("\n");

        if (myNotes != null && !myNotes.isEmpty()) {
            sb.append("Notes: ").append(myNotes).append("\n");
        }

        sb.append("\nGiving Away (").append(myGivingItems.size()).append(" items):\n");
        for (TradeItem item : myGivingItems) {
            sb.append("  - ").append(item.toString()).append("\n");
        }
        sb.append("Total Giving: $").append(String.format("%.2f", getGivingTotal())).append("\n");

        sb.append("\nReceiving (").append(myReceivingItems.size()).append(" items):\n");
        for (TradeItem item : myReceivingItems) {
            sb.append("  - ").append(item.toString()).append("\n");
        }
        sb.append("Total Receiving: $").append(String.format("%.2f", getReceivingTotal())).append("\n");
        sb.append("Adjusted Receiving: $").append(String.format("%.2f", getAdjustedReceivingTotal())).append("\n");

        sb.append("\nDifference: $").append(String.format("%.2f", getDifference())).append("\n");
        sb.append("Adjusted Difference: $").append(String.format("%.2f", getAdjustedDifference())).append("\n");

        return sb.toString();
    }
}