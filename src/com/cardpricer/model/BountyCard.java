package com.cardpricer.model;

import java.math.BigDecimal;

/**
 * Immutable value class representing a Bounty Board entry.
 *
 * <p>A bounty card overrides the standard tiered buy rates for a specific
 * card printing identified by set code + collector number. Bounties always
 * take priority over tiered rules.
 *
 * <p>UI editing is deferred to a future release; persistence and lookup
 * are fully operational now.
 */
public final class BountyCard {

    /** Set code for the targeted printing (e.g. {@code "TDM"}). */
    public final String setCode;

    /** Collector number for the targeted printing (e.g. {@code "3"}). */
    public final String collectorNumber;

    /** Human-readable card name for display purposes. */
    public final String cardName;

    /** Credit payout rate override, e.g. {@code 0.70} for 70%. */
    public final BigDecimal creditRate;

    /** Check payout rate override, e.g. {@code 0.50} for 50%. */
    public final BigDecimal checkRate;

    /**
     * Creates a new bounty card entry.
     *
     * @param setCode         set code (case-insensitive during lookup)
     * @param collectorNumber collector number
     * @param cardName        display name
     * @param creditRate      credit payout rate
     * @param checkRate       check payout rate
     */
    public BountyCard(String setCode, String collectorNumber, String cardName,
                      BigDecimal creditRate, BigDecimal checkRate) {
        this.setCode         = setCode;
        this.collectorNumber = collectorNumber;
        this.cardName        = cardName;
        this.creditRate      = creditRate;
        this.checkRate       = checkRate;
    }

    /**
     * Returns {@code true} when this bounty matches the given set code and
     * collector number (case-insensitive comparison).
     *
     * @param setCode         set code to test
     * @param collectorNumber collector number to test
     * @return whether this bounty applies
     */
    public boolean matches(String setCode, String collectorNumber) {
        return this.setCode.equalsIgnoreCase(setCode)
            && this.collectorNumber.equalsIgnoreCase(collectorNumber);
    }

    /**
     * Returns the lookup key used in the bounty {@code HashMap}.
     * Format: {@code "SETCODE/collectorNumber"} (set code upper-cased).
     *
     * @return the HashMap key for this bounty
     */
    public String key() {
        return setCode.toUpperCase() + "/" + collectorNumber;
    }

    @Override
    public String toString() {
        return String.format("BountyCard{%s/%s \"%s\" credit=%.0f%% check=%.0f%%}",
                setCode, collectorNumber, cardName,
                creditRate.multiply(new BigDecimal("100")),
                checkRate.multiply(new BigDecimal("100")));
    }
}
