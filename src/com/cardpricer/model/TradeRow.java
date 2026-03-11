package com.cardpricer.model;

import com.cardpricer.service.BuyRateService;

/**
 * Aggregate object replacing three parallel lists in TradePanel:
 * {@code List<TradeItem> receivedCards}, {@code List<String> cardConditions},
 * and {@code List<BuyRateService.PayoutResult> rowPayouts}.
 */
public final class TradeRow {
    public final TradeItem item;
    public String condition;
    public BuyRateService.PayoutResult payout;

    public TradeRow(TradeItem item, String condition) {
        this.item = item;
        this.condition = condition;
    }
}
