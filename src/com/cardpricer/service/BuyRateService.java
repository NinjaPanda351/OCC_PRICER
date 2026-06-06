package com.cardpricer.service;

import com.cardpricer.gui.panel.PreferencesPanel;
import com.cardpricer.model.BountyCard;
import com.cardpricer.model.BuyRateRule;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
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
 *   <li>Check the bounty map by card name — bounty always wins if found.</li>
 *   <li>Walk rules sorted descending by threshold; first match wins.</li>
 *   <li>If no rules loaded (corruption guard), use hardcoded defaults (50% / 33.33%).</li>
 * </ol>
 *
 * <h3>Reload / generation counter</h3>
 * {@link TradePanel} polls {@link #getSaveGeneration()} on focus gain and
 * calls {@link #reload()} when the generation changes.
 */
public class BuyRateService {

    private static final String RULES_KEY       = "buy.rate.rules";
    private static final String BOUNTIES_KEY    = "buy.rate.bounties";
    private static final String SHARED_FILE     = "buy_rates.json";

    private static final BigDecimal DEFAULT_CREDIT    = new BigDecimal("0.50");
    private static final BigDecimal DEFAULT_CHECK     = new BigDecimal("0.3333");
    private static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.ZERO;

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(PreferencesPanel.class);

    /** Monotonically increasing counter; incremented each time rules or bounties are saved. */
    private static volatile int saveGeneration = 0;

    /** Rules sorted descending by thresholdMin (highest threshold first). */
    private List<BuyRateRule> rules = new ArrayList<>();

    /** Bounty map keyed by card name upper-cased. */
    private Map<String, BountyCard> bounties = new HashMap<>();

    /** Last-modified timestamp of the shared buy_rates.json file as seen by this instance. */
    private long lastSharedModified = 0L;

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
     * @param setCode         card set code (may be "MISC"; kept for future printing-specific use)
     * @param collectorNumber collector number (kept for future printing-specific use)
     * @param cardName        card name for bounty lookup (case-insensitive); may be null
     * @param marketValue     unit market price
     * @return payout result with amounts and rates applied
     */
    public PayoutResult computePayout(String setCode, String collectorNumber,
                                      String cardName, BigDecimal marketValue) {
        // 1. Bounty lookup by card name
        if (cardName != null && !cardName.isEmpty()) {
            BountyCard bounty = bounties.get(cardName.toUpperCase());
            if (bounty != null) {
                BigDecimal credit = marketValue.multiply(bounty.creditRate).setScale(2, RoundingMode.HALF_UP);
                BigDecimal check  = marketValue.multiply(bounty.checkRate).setScale(2, RoundingMode.HALF_UP);
                return new PayoutResult(credit, check, bounty.creditRate, bounty.checkRate, true);
            }
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
     * Re-reads rules and bounties. Checks the shared folder first; if a
     * {@code buy_rates.json} exists there that is newer than the last-seen
     * version, loads from it and back-fills local preferences.  Falls back
     * to local preferences when no shared file is available.
     */
    public void reload() {
        if (loadFromSharedFileIfNewer()) return;
        rules    = loadRulesFromPrefs();
        bounties = loadBountiesFromPrefs();
    }

    /**
     * Polls the shared folder for a {@code buy_rates.json} that is newer
     * than the last-seen version.  If found, loads it, updates local
     * preferences, and increments {@link #saveGeneration} so that
     * {@code TradePanel} refreshes its payout display.
     *
     * <p>Called by {@code TradePanel} on panel-show and before each summary
     * refresh so cross-machine rate changes are picked up automatically.
     */
    public void pollSharedFolder() {
        if (loadFromSharedFileIfNewer()) {
            saveGeneration++;
        }
    }

    /**
     * Returns the current rules list in ascending threshold order (suitable for table display).
     *
     * @return list of rules ascending by threshold
     */
    public List<BuyRateRule> getRules() {
        List<BuyRateRule> ascending = new ArrayList<>(rules);
        ascending.sort(Comparator.comparing(r -> r.thresholdMin));
        return ascending;
    }

    /**
     * Returns the current bounties list sorted by card name.
     *
     * @return list of all bounty cards sorted alphabetically by name
     */
    public List<BountyCard> getBounties() {
        List<BountyCard> list = new ArrayList<>(bounties.values());
        list.sort(Comparator.comparing(b -> b.cardName.toUpperCase()));
        return list;
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

        writeRulesToPrefs(newRules);
        rules = buildDescendingList(newRules);
        saveGeneration++;
        writeToSharedFolder();
    }

    /**
     * Saves bounty cards to preferences and increments the save generation.
     *
     * @param newBounties bounties to persist
     */
    public void saveBounties(List<BountyCard> newBounties) {
        writeBountiesToPrefs(newBounties);
        bounties = buildBountyMap(newBounties);
        saveGeneration++;
        writeToSharedFolder();
    }

    /**
     * Parses a CSV file into a list of {@link BountyCard} objects.
     *
     * <p>Expected format:
     * <pre>
     * CARD NAME,CREDIT PERCENT,CHECK PERCENT
     * Black Lotus,80,60
     * Ancestral Recall,75,55
     * </pre>
     * Lines starting with {@code #} and blank lines are skipped. The first
     * header line (containing "CARD NAME") is also skipped.
     *
     * @param file CSV file to parse
     * @return list of parsed bounty cards
     * @throws IOException if the file cannot be read or a data line is malformed
     */
    public List<BountyCard> parseBountyCsv(File file) throws IOException {
        List<BountyCard> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                // Skip header row
                if (trimmed.toUpperCase().startsWith("CARD NAME")) continue;

                String[] parts = trimmed.split(",", 3);
                if (parts.length < 3) {
                    throw new IOException("Line " + lineNum + ": expected 3 columns, got " + parts.length);
                }
                try {
                    String name       = parts[0].trim();
                    BigDecimal credit = new BigDecimal(parts[1].trim()).divide(new BigDecimal("100"));
                    BigDecimal check  = new BigDecimal(parts[2].trim()).divide(new BigDecimal("100"));
                    if (name.isEmpty()) {
                        throw new IOException("Line " + lineNum + ": card name is empty");
                    }
                    result.add(new BountyCard(name, credit, check));
                } catch (NumberFormatException e) {
                    throw new IOException("Line " + lineNum + ": " + e.getMessage());
                }
            }
        }
        return result;
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
    // Private helpers — prefs
    // -------------------------------------------------------------------------

    private void writeRulesToPrefs(List<BuyRateRule> ruleList) {
        PREFS.put(RULES_KEY, buildRulesJson(ruleList).toString());
    }

    private void writeBountiesToPrefs(Collection<BountyCard> bountyList) {
        PREFS.put(BOUNTIES_KEY, buildBountiesJson(bountyList).toString());
    }

    private List<BuyRateRule> loadRulesFromPrefs() {
        String json = PREFS.get(RULES_KEY, "");
        List<BuyRateRule> list = new ArrayList<>();
        if (!json.isBlank()) {
            try {
                list = parseRulesJson(new JSONArray(json));
            } catch (Exception e) {
                System.err.println("[BuyRateService] Failed to parse rules from prefs: " + e.getMessage());
            }
        }
        ensureCatchAll(list);
        return buildDescendingList(list);
    }

    private Map<String, BountyCard> loadBountiesFromPrefs() {
        String json = PREFS.get(BOUNTIES_KEY, "");
        if (json.isBlank()) return new HashMap<>();
        try {
            return buildBountyMap(parseBountiesJson(new JSONArray(json)));
        } catch (Exception e) {
            System.err.println("[BuyRateService] Failed to parse bounties from prefs: " + e.getMessage());
            return new HashMap<>();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — shared folder
    // -------------------------------------------------------------------------

    /** Resolves the shared folder from the same Preferences node used by PreferencesPanel. */
    private static File resolveSharedFolder() {
        String path = PREFS.get(PreferencesPanel.SHARED_FOLDER_KEY, "");
        if (path.isBlank()) return null;
        File dir = new File(path);
        return (dir.exists() && dir.isDirectory()) ? dir : null;
    }

    /**
     * Writes current rules and bounties to {@code buy_rates.json} in the
     * shared folder.  Best-effort — failures are logged but not propagated.
     */
    private void writeToSharedFolder() {
        File dir = resolveSharedFolder();
        if (dir == null) return;
        try {
            JSONObject root = new JSONObject();
            root.put("rules",    buildRulesJson(rules));
            root.put("bounties", buildBountiesJson(bounties.values()));
            File dest = new File(dir, SHARED_FILE);
            Files.writeString(dest.toPath(), root.toString(2));
            lastSharedModified = dest.lastModified();
            System.out.println("[BuyRateService] Wrote buy_rates.json to shared folder.");
        } catch (Exception e) {
            System.err.println("[BuyRateService] Failed to write shared buy_rates.json: " + e.getMessage());
        }
    }

    /**
     * Loads rules and bounties from the shared {@code buy_rates.json} if it
     * is newer than the last version seen by this instance.  Updates local
     * preferences so future restarts stay in sync.
     *
     * @return {@code true} if new data was loaded from the shared file
     */
    private boolean loadFromSharedFileIfNewer() {
        File dir = resolveSharedFolder();
        if (dir == null) return false;
        File sharedFile = new File(dir, SHARED_FILE);
        if (!sharedFile.exists()) return false;
        long fileModified = sharedFile.lastModified();
        if (fileModified <= lastSharedModified) return false;

        try {
            JSONObject root      = new JSONObject(Files.readString(sharedFile.toPath()));
            List<BuyRateRule> r  = parseRulesJson(root.optJSONArray("rules"));
            List<BountyCard>  b  = parseBountiesJson(root.optJSONArray("bounties"));
            ensureCatchAll(r);
            this.rules    = buildDescendingList(r);
            this.bounties = buildBountyMap(b);
            lastSharedModified = fileModified;
            // Mirror to local prefs so the app works offline next time
            writeRulesToPrefs(r);
            writeBountiesToPrefs(b);
            System.out.println("[BuyRateService] Synced buy rates from shared folder.");
            return true;
        } catch (Exception e) {
            System.err.println("[BuyRateService] Failed to read shared buy_rates.json: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — JSON
    // -------------------------------------------------------------------------

    private static JSONArray buildRulesJson(Iterable<BuyRateRule> ruleList) {
        JSONArray arr = new JSONArray();
        for (BuyRateRule rule : ruleList) {
            arr.put(new JSONObject()
                    .put("thresholdMin", rule.thresholdMin.toPlainString())
                    .put("creditRate",   rule.creditRate.toPlainString())
                    .put("checkRate",    rule.checkRate.toPlainString()));
        }
        return arr;
    }

    private static JSONArray buildBountiesJson(Iterable<BountyCard> bountyList) {
        JSONArray arr = new JSONArray();
        for (BountyCard b : bountyList) {
            arr.put(new JSONObject()
                    .put("cardName",   b.cardName)
                    .put("creditRate", b.creditRate.toPlainString())
                    .put("checkRate",  b.checkRate.toPlainString()));
        }
        return arr;
    }

    private static List<BuyRateRule> parseRulesJson(JSONArray arr) {
        List<BuyRateRule> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new BuyRateRule(
                        new BigDecimal(obj.getString("thresholdMin")),
                        new BigDecimal(obj.getString("creditRate")),
                        new BigDecimal(obj.getString("checkRate"))));
            } catch (Exception e) {
                System.err.println("[BuyRateService] Skipping malformed rule at index " + i);
            }
        }
        return list;
    }

    private static List<BountyCard> parseBountiesJson(JSONArray arr) {
        List<BountyCard> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new BountyCard(
                        obj.getString("cardName"),
                        new BigDecimal(obj.getString("creditRate")),
                        new BigDecimal(obj.getString("checkRate"))));
            } catch (Exception e) {
                System.err.println("[BuyRateService] Skipping malformed bounty at index " + i);
            }
        }
        return list;
    }

    private void ensureCatchAll(List<BuyRateRule> list) {
        boolean hasCatchAll = list.stream()
                .anyMatch(r -> r.thresholdMin.compareTo(BigDecimal.ZERO) == 0);
        if (!hasCatchAll) {
            list.add(new BuyRateRule(DEFAULT_THRESHOLD, DEFAULT_CREDIT, DEFAULT_CHECK));
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — collections
    // -------------------------------------------------------------------------

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
