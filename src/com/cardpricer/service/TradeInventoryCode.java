package com.cardpricer.service;

import com.cardpricer.model.TradeItem;
import com.cardpricer.util.SetList;
import java.util.Objects;
import java.util.regex.Pattern;

/** Store inventory aliases for trade exports; never changes the received printing or its price. */
final class TradeInventoryCode {
    private static final Pattern PROMO_NUMBER = Pattern.compile("(?i)^([0-9]+)(?:[ps]★?|★)?$");

    record Mapping(String code, boolean promo) {}

    private TradeInventoryCode() {}

    static Mapping map(TradeItem item) {
        var card = item.getCard();
        String set = SetList.fromScryfallCode(card.getSetCode());
        String number = card.getCollectorNumber();
        // Only P-prefixed versions of known base sets are set promos. PLST, PIP,
        // PCY and standalone promotional sets keep their existing identifiers.
        if (set.startsWith("P") && !SetList.isValidSetCode(set)) {
            String base = SetList.fromScryfallCode(set.substring(1));
            var match = PROMO_NUMBER.matcher(number);
            if (SetList.isValidSetCode(base) && match.matches()) {
                return new Mapping(base + " " + match.group(1) + "F", true);
            }
        }
        return new Mapping(("PLST".equals(set) ? number.replace('-', ' ') : set + " " + number)
                + item.getFinishType(), false);
    }

    /** Intentional promo/base aliases may share stock; unrelated printing collisions still fail. */
    static boolean canShareCode(TradeItem first, TradeItem second) {
        var a = first.getCard();
        var b = second.getCard();
        if (a.identity().equals(b.identity())) return true;
        return (map(first).promo() || map(second).promo())
                && Objects.equals(a.getName(), b.getName())
                && Objects.equals(a.getLanguage(), b.getLanguage());
    }
}
