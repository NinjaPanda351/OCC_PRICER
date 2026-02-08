package com.cardpricer.model;

import java.math.BigDecimal;

/**
 * Represents a single card in a trade (either giving or receiving)
 * Similar to OrderItem but specifically for trades
 */
public class TradeItem {
    private Card myCard;
    private boolean myIsFoil;
    private int myQuantity;
    private BigDecimal myUnitPrice;

    /**
     * Creates a TradeItem with quantity of 1
     * @param theCard The card being traded
     * @param isFoil Whether this is the foil version
     */
    public TradeItem(final Card theCard, final boolean isFoil) {
        this(theCard, isFoil, 1);
    }

    /**
     * Creates a TradeItem with specified quantity
     * @param theCard The card being traded
     * @param isFoil Whether this is the foil version
     * @param theQuantity How many of this card
     */
    public TradeItem(final Card theCard, final boolean isFoil, final int theQuantity) {
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

    /**
     * Creates a TradeItem with specified quantity and finish type
     * @param theCard The card being traded
     * @param isFoil Whether this is the foil/etched version
     * @param theQuantity How many of this card
     * @param finishType "F" for foil, "E" for etched, "" for normal
     */
    public TradeItem(final Card theCard, final boolean isFoil, final int theQuantity, final String finishType) {
        if (theCard == null) {
            throw new IllegalArgumentException("Card cannot be null");
        }
        if (theQuantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        this.myCard = theCard;
        this.myIsFoil = isFoil;
        this.myQuantity = theQuantity;

        // Set unit price based on finish type
        if ("F".equals(finishType)) {
            this.myUnitPrice = theCard.getFoilPriceAsBigDecimal();
        } else if ("E".equals(finishType)) {
            this.myUnitPrice = theCard.getEtchedPriceAsBigDecimal();
        } else {
            this.myUnitPrice = theCard.getPriceAsBigDecimal();
        }
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
     * Calculates the total price for this trade item
     * @return Unit price × quantity
     */
    public BigDecimal getTotalPrice() {
        return myUnitPrice.multiply(BigDecimal.valueOf(myQuantity));
    }

    /**
     * Returns the finish type as a string
     */
    public String getFinish() {
        return myIsFoil ? "Foil" : "Normal";
    }

    /**
     * Returns the set collector code (e.g., "COK 1" or "COK 1F")
     */
    public String getSetCollectorCode() {
        return myCard.getSetCode() + " " +
                myCard.getCollectorNumber() +
                (myIsFoil ? "F" : "");
    }

    /**
     * Returns the full display name including frame effects
     */
    public String getDisplayName() {
        StringBuilder nameBuilder = new StringBuilder(myCard.getName());

        String frameDisplay = myCard.getFrameEffectDisplay();
        if (frameDisplay != null) {
            nameBuilder.append(" - ").append(frameDisplay);
        }

        return nameBuilder.toString();
    }

    /**
     * Checks if this trade item has a valid (non-zero) price
     */
    public boolean hasValidPrice() {
        return myUnitPrice != null && myUnitPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    public void setUnitPrice(final BigDecimal theUnitPrice) {
        this.myUnitPrice = theUnitPrice;
    }

    @Override
    public String toString() {
        return String.format("%s %s x%d @ $%.2f = $%.2f",
                getSetCollectorCode(),
                myCard.getName(),
                myQuantity,
                myUnitPrice,
                getTotalPrice());
    }
}