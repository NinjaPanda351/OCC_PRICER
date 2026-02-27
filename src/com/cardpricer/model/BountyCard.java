package com.cardpricer.model;

import java.math.BigDecimal;

/**
 * Immutable value class representing a Bounty Board entry.
 *
 * <p>A bounty card overrides the standard tiered buy rates for any card
 * matching by name (case-insensitive, regardless of printing or finish).
 * Bounties always take priority over tiered rules.
 */
public final class BountyCard {

    /** Card name for matching (case-insensitive). */
    public final String cardName;

    /** Credit payout rate override, e.g. {@code 0.70} for 70%. */
    public final BigDecimal creditRate;

    /** Check payout rate override, e.g. {@code 0.50} for 50%. */
    public final BigDecimal checkRate;

    /**
     * Creates a new bounty card entry.
     *
     * @param cardName   display name (used for case-insensitive matching)
     * @param creditRate credit payout rate
     * @param checkRate  check payout rate
     */
    public BountyCard(String cardName, BigDecimal creditRate, BigDecimal checkRate) {
        this.cardName   = cardName;
        this.creditRate = creditRate;
        this.checkRate  = checkRate;
    }

    /**
     * Returns {@code true} when this bounty matches the given card name
     * (case-insensitive comparison).
     *
     * @param name card name to test
     * @return whether this bounty applies
     */
    public boolean matches(String name) {
        return this.cardName.equalsIgnoreCase(name);
    }

    /**
     * Returns the lookup key used in the bounty {@code HashMap}.
     * The key is the card name upper-cased for case-insensitive lookup.
     *
     * @return the HashMap key for this bounty
     */
    public String key() {
        return cardName.toUpperCase();
    }

    @Override
    public String toString() {
        return String.format("BountyCard{\"%s\" credit=%.0f%% check=%.0f%%}",
                cardName,
                creditRate.multiply(new BigDecimal("100")),
                checkRate.multiply(new BigDecimal("100")));
    }
}
