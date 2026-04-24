package com.cardpricer.util;

import java.math.BigDecimal;

/**
 * Application-wide constants for card pricing, conditions, and configuration.
 * All fields are {@code public static final}; this class is not instantiable.
 */
public final class CardConstants {

    private CardConstants() {}

    /** Condition names in display order from best to worst. */
    public static final String[] CONDITIONS = {"NM", "LP", "MP", "HP", "DMG"};

    /**
     * Per-condition price multipliers.
     * Each tier is 80 % of the previous: NM=1.0, LP=0.8, MP=0.64, HP=0.512, DMG=0.4096.
     */
    public static final double[] CONDITION_MULTIPLIERS = {1.0, 0.8, 0.64, 0.512, 0.4096};

    /** Minimum buylist price for Rare and Mythic cards. */
    public static final BigDecimal RARITY_MIN_RARE_MYTHIC = new BigDecimal("0.50");

    /** Minimum buylist price for Uncommon cards. */
    public static final BigDecimal RARITY_MIN_UNCOMMON = new BigDecimal("0.25");

    /** Minimum buylist price for Common and all other rarities. */
    public static final BigDecimal RARITY_MIN_COMMON = new BigDecimal("0.10");

    /**
     * Price threshold for rounding step selection.
     * Below this value prices round to {@link #ROUNDING_STEP_LOW}; at or above they round to $1.00.
     */
    public static final BigDecimal ROUNDING_THRESHOLD = BigDecimal.TEN;

    /** Rounding step used for prices below {@link #ROUNDING_THRESHOLD}: $0.50 increments. */
    public static final BigDecimal ROUNDING_STEP_LOW = new BigDecimal("0.5");

    /** Payout rate for store-credit payments: 50 % of market price. */
    public static final BigDecimal PAYMENT_RATE_CREDIT = new BigDecimal("0.50");

    /** Payout rate for check payments: 33 % of market price (display approximation). */
    public static final BigDecimal PAYMENT_RATE_CHECK = new BigDecimal("0.33");

    /** Payout rate for partial (credit + check) payments: weighted average ≈ 41.67 %. */
    public static final BigDecimal PAYMENT_RATE_PARTIAL = new BigDecimal("0.4167");

    /** Exact divisor for check-payment cost calculation (price ÷ 3). */
    public static final BigDecimal PAYMENT_DIVISOR_CHECK = new BigDecimal("3");

    /**
     * Minimum delay between individual card lookups (/cards/{set}/{num} — 10 req/sec limit = 100 ms).
     * Kept at 1000 ms for conservative compliance. Used between sets in BulkPricerPanel
     * and between lookups in PasteImportDialog.
     * Note: /cards/search pagination uses ScryfallApiService.SEARCH_RATE_LIMIT_MS (500 ms).
     */
    public static final int API_RATE_LIMIT_MS = 1000;

    /** Debounce delay for the card-preview live-search field (ms). */
    public static final int PREVIEW_DEBOUNCE_MS = 400;
}
