package com.cardpricer.service;

import com.cardpricer.gui.panel.PreferencesPanel;
import com.cardpricer.model.BountyCard;
import com.cardpricer.model.BuyRateRule;
import com.cardpricer.util.AppDataDirectory;
import com.cardpricer.util.SharedFolderLocator;
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
 *   <li>If no rules loaded (corruption guard), use hardcoded defaults (50% / 33%).</li>
 * </ol>
 *
 * <h3>Reload / generation counter</h3>
 * {@link TradePanel} polls {@link #getSaveGeneration()} on focus gain and
 * calls {@link #reload()} when the generation changes.
 */
public class BuyRateService {

    // ── JSON file names (written to SharedFolderLocator.configDir()) ─────────

    private static final String RULES_FILE    = "buy_rates.json";
    private static final String BOUNTIES_FILE = "bounties.json";

    // ── Legacy Preferences keys (one-time migration only) ─────────────────────

    private static final String LEGACY_RULES_KEY    = "buy.rate.rules";
    private static final String LEGACY_BOUNTIES_KEY = "buy.rate.bounties";
    private static final Preferences LEGACY_PREFS   =
            Preferences.userNodeForPackage(PreferencesPanel.class);

    // ── Rate defaults ─────────────────────────────────────────────────────────

    private static final BigDecimal DEFAULT_CREDIT    = new BigDecimal("0.50");
    private static final BigDecimal DEFAULT_CHECK     = new BigDecimal("0.33");
    private static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.ZERO;

    /** Monotonically increasing counter; incremented each time rules or bounties are saved. */
    private static volatile int saveGeneration = 0;

    /** Rules sorted descending by thresholdMin (highest threshold first). */
    private List<BuyRateRule> rules = new ArrayList<>();

    /** Bounty map keyed by card name upper-cased. */
    private Map<String, BountyCard> bounties = new HashMap<>();

    /** Returns the application-wide singleton instance. */
    public static BuyRateService getInstance() { return Holder.INSTANCE; }
    private static final class Holder {
        static final BuyRateService INSTANCE = new BuyRateService();
    }

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
     * Re-reads rules and bounties from their JSON config files.
     * Call this when {@link #getSaveGeneration()} returns a new value.
     */
    public void reload() {
        rules    = loadRules();
        bounties = loadBounties();
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
     * Validates and saves the given rules list to the JSON config file.
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
        writeConfigJson(RULES_FILE, arr.toString());

        rules = buildDescendingList(newRules);
        saveGeneration++;
    }

    /**
     * Saves bounty cards to the JSON config file and increments the save generation.
     *
     * @param newBounties bounties to persist
     */
    public void saveBounties(List<BountyCard> newBounties) {
        JSONArray arr = new JSONArray();
        for (BountyCard b : newBounties) {
            JSONObject obj = new JSONObject();
            obj.put("cardName",   b.cardName);
            obj.put("creditRate", b.creditRate.toPlainString());
            obj.put("checkRate",  b.checkRate.toPlainString());
            arr.put(obj);
        }
        writeConfigJson(BOUNTIES_FILE, arr.toString());

        bounties = buildBountyMap(newBounties);
        saveGeneration++;
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
    // Private helpers
    // -------------------------------------------------------------------------

    private List<BuyRateRule> loadRules() {
        String json = readConfigJson(RULES_FILE, LEGACY_RULES_KEY);
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
                list.clear();
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

    private Map<String, BountyCard> loadBounties() {
        String json = readConfigJson(BOUNTIES_FILE, LEGACY_BOUNTIES_KEY);
        List<BountyCard> list = new ArrayList<>();

        if (!json.isBlank()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String cardName   = obj.getString("cardName");
                    BigDecimal credit = new BigDecimal(obj.getString("creditRate"));
                    BigDecimal check  = new BigDecimal(obj.getString("checkRate"));
                    list.add(new BountyCard(cardName, credit, check));
                }
            } catch (Exception e) {
                System.err.println("[BuyRateService] Failed to parse bounties: " + e.getMessage());
            }
        }

        return buildBountyMap(list);
    }

    /**
     * Reads JSON from a config file, with fallback chain:
     * 1. shared (or local) configDir / filename
     * 2. local AppData config / filename  (when shared dir differs)
     * 3. legacy Preferences key           (one-time migration)
     */
    private static String readConfigJson(String filename, String legacyKey) {
        File primary = new File(SharedFolderLocator.configDir(), filename);
        if (primary.exists()) {
            try { return Files.readString(primary.toPath()); }
            catch (IOException e) {
                System.err.println("[BuyRateService] Cannot read " + primary + ": " + e.getMessage());
            }
        }

        // Fallback: local AppData config (user just configured shared folder)
        File local = new File(AppDataDirectory.config(), filename);
        if (local.exists() && !local.getAbsolutePath().equals(primary.getAbsolutePath())) {
            try { return Files.readString(local.toPath()); }
            catch (IOException e) {
                System.err.println("[BuyRateService] Cannot read " + local + ": " + e.getMessage());
            }
        }

        // Last resort: migrate from legacy Preferences (Windows Registry)
        String fromPrefs = LEGACY_PREFS.get(legacyKey, "");
        if (!fromPrefs.isBlank()) {
            System.out.println("[BuyRateService] Migrating '" + legacyKey + "' from Preferences → " + primary);
            writeConfigJson(filename, fromPrefs);
            LEGACY_PREFS.remove(legacyKey);
        }
        return fromPrefs;
    }

    private static void writeConfigJson(String filename, String json) {
        File f = new File(SharedFolderLocator.configDir(), filename);
        try {
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), json);
        } catch (IOException e) {
            System.err.println("[BuyRateService] Cannot write " + f + ": " + e.getMessage());
        }
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
