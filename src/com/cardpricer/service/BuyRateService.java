package com.cardpricer.service;

import com.cardpricer.gui.panel.PreferencesPanel;
import com.cardpricer.model.BountyCard;
import com.cardpricer.model.BuyRateRule;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Owns persistence, defaults, and rate lookup for tiered buy rates and
 * bounty card overrides.
 *
 * <p>Uses the same {@link java.util.prefs.Preferences} node as
 * {@link PreferencesPanel} so all settings live under one node.
 *
 * <h3>Lookup algorithm ({@link #computePayout})</h3>
 * <ol>
 *   <li>Check the bounty map — bounty always wins if found.</li>
 *   <li>Walk rules sorted descending by threshold; first match wins.</li>
 *   <li>If no rules loaded (corruption guard), use hardcoded defaults (50% / 33%).</li>
 * </ol>
 *
 * <h3>Reload / generation counter</h3>
 * {@link TradePanel} polls {@link #getSaveGeneration()} on focus gain and
 * calls {@link #reload()} when the generation changes.
 */
public class BuyRateService {

    private static final String RULES_KEY    = "buy.rate.rules";
    private static final String BOUNTIES_KEY = "buy.rate.bounties";

    private static final BigDecimal DEFAULT_CREDIT = new BigDecimal("0.50");
    private static final BigDecimal DEFAULT_CHECK  = new BigDecimal("0.33");
    private static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.ZERO;

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(PreferencesPanel.class);

    /** Monotonically increasing counter; incremented each time rules or bounties are saved. */
    private static volatile int saveGeneration = 0;

    /** Rules sorted descending by thresholdMin (highest threshold first). */
    private List<BuyRateRule> rules = new ArrayList<>();

    /** Bounty map keyed by {@code BountyCard.key()}. */
    private Map<String, BountyCard> bounties = new HashMap<>();

    /**
     * Creates the service and loads persisted rules/bounties immediately.
     */
    public BuyRateService() {
        reload();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Result returned by {@link #computePayout}.
     *
     * @param creditPayout      credit payout amount (market × creditRate × qty)
     * @param checkPayout       check payout amount (market × checkRate × qty)
     * @param appliedCreditRate rate used for the credit calculation
     * @param appliedCheckRate  rate used for the check calculation
     * @param isBounty          {@code true} when a bounty override was applied
     */
    public record PayoutResult(
            BigDecimal creditPayout,
            BigDecimal checkPayout,
            BigDecimal appliedCreditRate,
            BigDecimal appliedCheckRate,
            boolean isBounty) {}

    /**
     * Computes the credit and check payout amounts for a single card unit.
     * Multiply by quantity in the caller.
     *
     * @param setCode         card set code (may be "MISC")
     * @param collectorNumber collector number
     * @param marketValue     unit market price
     * @return payout result with amounts and rates applied
     */
    public PayoutResult computePayout(String setCode, String collectorNumber, BigDecimal marketValue) {
        // 1. Bounty lookup
        String key = (setCode == null ? "" : setCode.toUpperCase()) + "/" + (collectorNumber == null ? "" : collectorNumber);
        BountyCard bounty = bounties.get(key);
        if (bounty != null) {
            BigDecimal credit = marketValue.multiply(bounty.creditRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal check  = marketValue.multiply(bounty.checkRate).setScale(2, RoundingMode.HALF_UP);
            return new PayoutResult(credit, check, bounty.creditRate, bounty.checkRate, true);
        }

        // 2. Tiered rules (sorted descending by threshold, so highest wins first)
        for (BuyRateRule rule : rules) {
            if (rule.matches(marketValue)) {
                BigDecimal credit = marketValue.multiply(rule.creditRate).setScale(2, RoundingMode.HALF_UP);
                BigDecimal check  = marketValue.multiply(rule.checkRate).setScale(2, RoundingMode.HALF_UP);
                return new PayoutResult(credit, check, rule.creditRate, rule.checkRate, false);
            }
        }

        // 3. Hardcoded defaults (corruption guard: rules list was empty)
        BigDecimal credit = marketValue.multiply(DEFAULT_CREDIT).setScale(2, RoundingMode.HALF_UP);
        BigDecimal check  = marketValue.multiply(DEFAULT_CHECK).setScale(2, RoundingMode.HALF_UP);
        return new PayoutResult(credit, check, DEFAULT_CREDIT, DEFAULT_CHECK, false);
    }

    /**
     * Re-reads rules and bounties from persistent preferences.
     * Call this when {@link #getSaveGeneration()} returns a new value.
     */
    public void reload() {
        rules    = loadRulesFromPrefs();
        bounties = loadBountiesFromPrefs();
    }

    /**
     * Returns the current rules list in ascending threshold order (suitable for table display).
     *
     * @return unmodifiable view of rules ascending by threshold
     */
    public List<BuyRateRule> getRules() {
        List<BuyRateRule> ascending = new ArrayList<>(rules);
        ascending.sort(Comparator.comparing(r -> r.thresholdMin));
        return ascending;
    }

    /**
     * Returns the current bounties list.
     *
     * @return list of all bounty cards
     */
    public List<BountyCard> getBounties() {
        return new ArrayList<>(bounties.values());
    }

    /**
     * Validates and saves the given rules list to preferences.
     *
     * <p>Validation: at least one rule with {@code thresholdMin == 0.00} must
     * exist to serve as the catch-all.
     *
     * @param newRules rules to persist (must contain a catch-all)
     * @throws IllegalArgumentException if no catch-all rule is present
     */
    public void saveRules(List<BuyRateRule> newRules) {
        boolean hasCatchAll = newRules.stream()
                .anyMatch(r -> r.thresholdMin.compareTo(BigDecimal.ZERO) == 0);
        if (!hasCatchAll) {
            throw new IllegalArgumentException(
                    "Rules must include a catch-all row with Min Price = $0.00.");
        }

        JSONArray arr = new JSONArray();
        for (BuyRateRule rule : newRules) {
            JSONObject obj = new JSONObject();
            obj.put("thresholdMin", rule.thresholdMin.toPlainString());
            obj.put("creditRate",   rule.creditRate.toPlainString());
            obj.put("checkRate",    rule.checkRate.toPlainString());
            arr.put(obj);
        }
        PREFS.put(RULES_KEY, arr.toString());

        // Reload cache and bump generation
        rules = buildDescendingList(newRules);
        saveGeneration++;
    }

    /**
     * Saves bounty cards to preferences and increments the save generation.
     *
     * @param newBounties bounties to persist
     */
    public void saveBounties(List<BountyCard> newBounties) {
        JSONArray arr = new JSONArray();
        for (BountyCard b : newBounties) {
            JSONObject obj = new JSONObject();
            obj.put("setCode",         b.setCode);
            obj.put("collectorNumber", b.collectorNumber);
            obj.put("cardName",        b.cardName);
            obj.put("creditRate",      b.creditRate.toPlainString());
            obj.put("checkRate",       b.checkRate.toPlainString());
            arr.put(obj);
        }
        PREFS.put(BOUNTIES_KEY, arr.toString());

        bounties = buildBountyMap(newBounties);
        saveGeneration++;
    }

    /**
     * Returns a monotonically increasing counter that increments each time
     * rules or bounties are saved.  {@link TradePanel} polls this to detect changes.
     *
     * @return current save generation
     */
    public static int getSaveGeneration() {
        return saveGeneration;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<BuyRateRule> loadRulesFromPrefs() {
        String json = PREFS.get(RULES_KEY, "");
        List<BuyRateRule> list = new ArrayList<>();

        if (!json.isBlank()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    BigDecimal threshold = new BigDecimal(obj.getString("thresholdMin"));
                    BigDecimal credit    = new BigDecimal(obj.getString("creditRate"));
                    BigDecimal check     = new BigDecimal(obj.getString("checkRate"));
                    list.add(new BuyRateRule(threshold, credit, check));
                }
            } catch (Exception e) {
                System.err.println("[BuyRateService] Failed to parse rules: " + e.getMessage());
                list.clear(); // fall through to defaults below
            }
        }

        // Always ensure a catch-all exists
        boolean hasCatchAll = list.stream()
                .anyMatch(r -> r.thresholdMin.compareTo(BigDecimal.ZERO) == 0);
        if (!hasCatchAll) {
            list.add(new BuyRateRule(DEFAULT_THRESHOLD, DEFAULT_CREDIT, DEFAULT_CHECK));
        }

        return buildDescendingList(list);
    }

    private Map<String, BountyCard> loadBountiesFromPrefs() {
        String json = PREFS.get(BOUNTIES_KEY, "");
        List<BountyCard> list = new ArrayList<>();

        if (!json.isBlank()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String setCode         = obj.getString("setCode");
                    String collectorNumber = obj.getString("collectorNumber");
                    String cardName        = obj.getString("cardName");
                    BigDecimal credit      = new BigDecimal(obj.getString("creditRate"));
                    BigDecimal check       = new BigDecimal(obj.getString("checkRate"));
                    list.add(new BountyCard(setCode, collectorNumber, cardName, credit, check));
                }
            } catch (Exception e) {
                System.err.println("[BuyRateService] Failed to parse bounties: " + e.getMessage());
            }
        }

        return buildBountyMap(list);
    }

    private static List<BuyRateRule> buildDescendingList(List<BuyRateRule> src) {
        List<BuyRateRule> desc = new ArrayList<>(src);
        desc.sort(Comparator.comparing((BuyRateRule r) -> r.thresholdMin).reversed());
        return desc;
    }

    private static Map<String, BountyCard> buildBountyMap(List<BountyCard> src) {
        Map<String, BountyCard> map = new HashMap<>();
        for (BountyCard b : src) {
            map.put(b.key(), b);
        }
        return map;
    }
}
