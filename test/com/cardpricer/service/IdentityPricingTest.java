package com.cardpricer.service;
import com.cardpricer.model.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class IdentityPricingTest {
    @Test void providerRoundTripPreservesOptionalFacesAndFinishes() {
        JSONObject fixture = new JSONObject("""
            {"id":"provider", "name":"Two faces", "set":"plst", "collector_number":"ARB-73★", "lang":"en",
             "rarity":"rare", "finishes":["etched"], "prices":{"usd_etched":"12.34"},
             "card_faces":[{"artist":"An artist","image_uris":{"normal":"https://example.invalid/image"}}]}
            """);
        Card card = ProviderCardMapper.fromJson(fixture);
        Card restored = ProviderCardMapper.fromJson(ProviderCardMapper.toJson(card));
        assertEquals(card.identity(), restored.identity());
        assertEquals("PLST", restored.getSetCode()); assertEquals("ARB-73★", restored.getCollectorNumber());
        assertEquals("An artist", restored.getArtist()); assertEquals(card.getFinishes(), restored.getFinishes());
        assertFalse(restored.hasNormalPrice()); assertTrue(restored.hasEtchedPrice());
    }
    @Test void tradePriceOverrideDoesNotMutateProviderCard() {
        Card card = new Card("Card", "TST", "1"); card.setPrice("10");
        TradeItem item = new TradeItem(card, false); item.getCard().setPrice("20");
        assertEquals("10", card.getPrice());
    }
    @Test void nmIsIdentityAndConditionsAreMonotonic() {
        var pricing = new PricingService();
        for (String value : new String[]{"0.10", "0.25", "0.50", "9.50", "10", "100"}) {
            BigDecimal base = pricing.applyPricingRules(new BigDecimal(value), "common");
            assertEquals(base, pricing.applyConditionMultiplier(base, "NM"));
            BigDecimal previous = base;
            for (Condition condition : Condition.values()) {
                BigDecimal current = pricing.applyConditionMultiplier(base, condition.name());
                assertTrue(current.signum() > 0); assertTrue(current.compareTo(previous) <= 0); previous = current;
            }
        }
    }
    @Test void bountyAndRuleBoundariesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new BountyCard("Card", new BigDecimal("1.01"), new BigDecimal("0.4")));
        assertThrows(IllegalArgumentException.class, () -> new BuyRateRule(new BigDecimal("-1"), BigDecimal.ONE, BigDecimal.ONE));
        assertTrue(new BuyRateRule(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE).matches(BigDecimal.ZERO));
    }
}
