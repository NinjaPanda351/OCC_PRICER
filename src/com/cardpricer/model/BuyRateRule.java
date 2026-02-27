package com.cardpricer.model;

import java.math.BigDecimal;

/**
 * Immutable value class representing a tiered buy-rate rule.
 *
 * <p>A rule applies when a card's market value is strictly greater than
 * {@code thresholdMin}. Rules are evaluated in descending threshold order so
 * the highest matching threshold wins.
 *
 * <p>The catch-all rule has {@code thresholdMin = 0.00} and always matches.
 */
public final class BuyRateRule {

    /** Minimum market value (exclusive) at which this rule activates. */
    public final BigDecimal thresholdMin;

    /** Credit payout rate, e.g. {@code 0.50} for 50%. Must be in (0, 1]. */
    public final BigDecimal creditRate;

    /** Check payout rate, e.g. {@code 0.33} for 33%. Must be in (0, 1]. */
    public final BigDecimal checkRate;

    /**
     * Creates a new buy-rate rule.
     *
     * @param thresholdMin minimum market value (exclusive); use {@code 0.00} for catch-all
     * @param creditRate   credit payout rate; must be in (0, 1]
     * @param checkRate    check payout rate; must be in (0, 1]
     * @throws IllegalArgumentException if either rate is outside (0, 1]
     */
    public BuyRateRule(BigDecimal thresholdMin, BigDecimal creditRate, BigDecimal checkRate) {
        if (creditRate.compareTo(BigDecimal.ZERO) <= 0 || creditRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("creditRate must be in (0, 1]: " + creditRate);
        }
        if (checkRate.compareTo(BigDecimal.ZERO) <= 0 || checkRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("checkRate must be in (0, 1]: " + checkRate);
        }
        this.thresholdMin = thresholdMin;
        this.creditRate   = creditRate;
        this.checkRate    = checkRate;
    }

    /**
     * Returns {@code true} when this rule applies to the given market value,
     * i.e. {@code marketValue > thresholdMin}.
     *
     * @param marketValue card's market value
     * @return whether this rule matches
     */
    public boolean matches(BigDecimal marketValue) {
        return marketValue.compareTo(thresholdMin) > 0;
    }

    @Override
    public String toString() {
        return String.format("BuyRateRule{threshold>$%.2f, credit=%.0f%%, check=%.0f%%}",
                thresholdMin,
                creditRate.multiply(new BigDecimal("100")),
                checkRate.multiply(new BigDecimal("100")));
    }
}
