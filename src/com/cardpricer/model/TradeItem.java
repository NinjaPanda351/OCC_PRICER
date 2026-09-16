package com.cardpricer.model;

import java.math.BigDecimal;

/**
 * Represents a single card in a trade (either giving or receiving)
 * Similar to OrderItem but specifically for trades
 */
public class TradeItem {
    private java.util.UUID lineId = java.util.UUID.randomUUID();
    private BigDecimal manualPrice;
    private String manualCondition;
    public BigDecimal getManualPrice() { return manualPrice; }
    public String getManualCondition() { return manualCondition; }
    public void setManualOverride(String condition,BigDecimal price) {
        if (price!=null && price.signum()<0) throw new IllegalArgumentException("Manual price must be non-negative");
        manualCondition=condition;manualPrice=price;
    }
    public java.util.UUID getLineId() { return lineId; }
    public void setLineId(java.util.UUID id) { lineId = java.util.Objects.requireNonNull(id); }
    private Card myCard;
    private boolean myIsFoil;
    /** Raw finish code: "F" (foil), "E" (etched), "S" (surge foil), or "" (normal). */
    private String myFinishType;
    private int myQuantity;
    private BigDecimal myUnitPrice;

    /**
     * Creates a TradeItem with quantity of 1 and no special finish.
     */
    public TradeItem(final Card theCard, final boolean isFoil) {
        this(theCard, isFoil, 1, isFoil ? "F" : "");
    }

    /**
     * Creates a TradeItem with specified quantity and no special finish.
     */
    public TradeItem(final Card theCard, final boolean isFoil, final int theQuantity) {
        this(theCard, isFoil, theQuantity, isFoil ? "F" : "");
    }

    /**
     * Creates a TradeItem with specified quantity and finish type.
     *
     * @param finishType "F" (foil), "E" (etched), "S" (surge foil), or "" (normal)
     */
    public TradeItem(final Card theCard, final boolean isFoil, final int theQuantity, final String finishType) {
        if (theCard == null) {
            throw new IllegalArgumentException("Card cannot be null");
        }
        if (theQuantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        this.myCard = theCard.copy();
        this.myFinishType = Finish.fromCode(finishType == null ? "" : finishType).code();
        this.myIsFoil = !this.myFinishType.isEmpty();
        this.myQuantity = theQuantity;

        // Set unit price based on finish type
        if ("F".equals(finishType) || "S".equals(finishType)) {
            this.myUnitPrice = theCard.getFoilPriceAsBigDecimal();
        } else if ("E".equals(finishType)) {
            this.myUnitPrice = theCard.getEtchedPriceAsBigDecimal();
        } else {
            this.myUnitPrice = theCard.getPriceAsBigDecimal();
        }
    }

    // Getters
    /** Returns the card represented by this trade item. */
    public Card getCard() {
        return myCard;
    }

    /** Returns {@code true} if this item uses a foil or special foil finish. */
    public boolean isFoil() {
        return myIsFoil;
    }

    /** Returns the quantity for this trade item. */
    public int getQuantity() {
        return myQuantity;
    }

    /**
     * Sets a new quantity for this trade item.
     *
     * @param theQuantity the new quantity; must be at least 1
     * @throws IllegalArgumentException if {@code theQuantity} is less than 1
     */
    public void setQuantity(final int theQuantity) {
        if (theQuantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        this.myQuantity = theQuantity;
    }

    /** Returns the per-unit price for this trade item. */
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
     * Returns the raw finish code: "F", "E", "S", or "".
     */
    public String getFinishType() {
        return myFinishType;
    }

    /**
     * Returns a human-readable finish label.
     */
    public String getFinish() {
        switch (myFinishType) {
            case "E": return "Etched";
            case "S": return "Surge Foil";
            case "F": return "Foil";
            default:  return "Normal";
        }
    }

    /**
     * Returns the set collector code (e.g., "COK 1" or "COK 1F")
     */
    public String getSetCollectorCode() {
        return myCard.getSetCode() + " " +
                myCard.getCollectorNumber() +
                myFinishType;
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

    /**
     * Overrides the unit price for this trade item (e.g. for manually adjusted prices).
     *
     * @param theUnitPrice new unit price; may be {@code null} to clear
     */
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
