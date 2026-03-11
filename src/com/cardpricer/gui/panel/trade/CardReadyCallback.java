package com.cardpricer.gui.panel.trade;

import com.cardpricer.model.Card;

/**
 * Callback fired by {@link TradeCardPreview} when a card is ready to be added to the trade.
 */
@FunctionalInterface
public interface CardReadyCallback {
    /**
     * @param card            the fetched (or manually-priced) card
     * @param finish          finish code: {@code ""} normal, {@code "F"} foil,
     *                        {@code "E"} etched, {@code "S"} surge foil
     * @param originalSetCode the raw set code as typed (e.g. {@code "plst"} for PLST cards)
     */
    void onCardReady(Card card, String finish, String originalSetCode);
}
