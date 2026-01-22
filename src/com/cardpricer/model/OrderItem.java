package com.cardpricer.model;

import java.math.BigDecimal;

/**
 * Represents a single line item in an order/invoice.
 * Contains the card, finish type, quantity, and calculated pricing.
 */
public class OrderItem {
    private Card myCard;
    private boolean myIsFoil;
    private int myQuantity;
    private BigDecimal myUnitPrice;

    /**
     * Creates an OrderItem with quantity of 1
     * @param theCard The card being ordered
     * @param isFoil Whether this is the foil version
     */
    public OrderItem(final Card theCard, final boolean isFoil) {
        this(theCard, isFoil, 1);
    }

    /**
     * Creates an OrderItem with specified quantity
     * @param theCard The card being ordered
     * @param isFoil Whether this is the foil version
     * @param theQuantity How many of this card
     */
    public OrderItem(final Card theCard, final boolean isFoil, final int theQuantity) {
        if (theCard == null) {
            throw new IllegalArgumentException("Card cannot be null");
        }
        if (theQuantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        this.myCard = theCard;
        this.myIsFoil = isFoil;
        this.myQuantity = theQuantity;

        // Set unit price based on finish
        this.myUnitPrice = isFoil ? theCard.getFoilPriceAsBigDecimal() :
                theCard.getPriceAsBigDecimal();
    }

    // Getters
    public Card getCard() {
        return myCard;
    }

    public boolean isFoil() {
        return myIsFoil;
    }

    public int getQuantity() {
        return myQuantity;
    }

    public void setQuantity(final int theQuantity) {
        if (theQuantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        this.myQuantity = theQuantity;
    }

    public BigDecimal getUnitPrice() {
        return myUnitPrice;
    }

    /**
     * Returns unit price as a double (for convenience)
     */
    public double getUnitPriceAsDouble() {
        return myUnitPrice.doubleValue();
    }

    /**
     * Calculates the total price for this line item
     * @return Unit price × quantity
     */
    public BigDecimal getTotalPrice() {
        return myUnitPrice.multiply(BigDecimal.valueOf(myQuantity));
    }

    /**
     * Returns total price as a double (for convenience)
     */
    public double getTotalPriceAsDouble() {
        return getTotalPrice().doubleValue();
    }

    /**
     * Returns the finish type as a string
     */
    public String getFinish() {
        return myIsFoil ? "Foil" : "Normal";
    }

    /**
     * Returns the set collector code (e.g., "TLA 1" or "TLA 1F")
     */
    public String getSetCollectorCode() {
        return myCard.getSetCode() + " " +
                myCard.getCollectorNumber() +
                (myIsFoil ? "F" : "");
    }

    /**
     * Increases the quantity by the specified amount
     */
    public void addQuantity(final int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.myQuantity += amount;
    }

    /**
     * Checks if this order item has a valid (non-zero) price
     */
    public boolean hasValidPrice() {
        return myUnitPrice != null && myUnitPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public String toString() {
        return String.format("%s %s x%d @ $%.2f = $%.2f",
                myCard.toString(),
                getFinish(),
                myQuantity,
                myUnitPrice,
                getTotalPrice());
    }

    /**
     * Returns a formatted display string for console output
     */
    public String toDisplayString() {
        return String.format("%-40s %-12s %-8s x%-3d $%-8.2f $%.2f",
                truncate(myCard.getName(), 40),
                getSetCollectorCode(),
                getFinish(),
                myQuantity,
                myUnitPrice,
                getTotalPrice());
    }

    /**
     * Helper method to truncate strings for display
     */
    private String truncate(String str, int length) {
        if (str == null || str.length() <= length) {
            return str;
        }
        return str.substring(0, length - 3) + "...";
    }
}