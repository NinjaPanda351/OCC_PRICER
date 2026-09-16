package com.cardpricer.service;


import com.cardpricer.model.BountyCard;
import com.cardpricer.model.BuyRateRule;
import com.cardpricer.util.AppDataDirectory;
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
 * <p>Rules and bounties are persisted to a local JSON file in the application
 * config directory ({@code buy_rates_local.json}) with no size restrictions.
 * The shared-folder path setting is still stored in {@link java.util.prefs.Preferences}.
 *
 * <h3>Lookup algorithm ({@link #computePayout})</h3>
 * <ol>
 *   <li>Check the bounty map by card name — bounty always wins if found.</li>
 *   <li>Walk rules sorted descending by threshold; first match wins.</li>
 *   <li>If no rules loaded (corruption guard), use hardcoded defaults (50% / 40%).</li>
 * </ol>
 *
 * <h3>Reload / generation counter</h3>
 * Trade views consume published in-memory snapshots; open preference editors retain
 * their revision until an explicit reload, so stale saves cannot erase another writer.
 */
public class BuyRateService {

    private static final String LOCAL_CONFIG_FILE = "buy_rates_local.json";
    private static final String SHARED_FILE       = "buy_rates.json";

    private static final BigDecimal DEFAULT_CREDIT    = new BigDecimal("0.50");
    private static final BigDecimal DEFAULT_CHECK     = new BigDecimal("0.40");
    private static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.ZERO;

    private static Preferences preferences() { return Preferences.userRoot().node("/com/cardpricer/gui/panel"); }

    /** Monotonically increasing counter; incremented each time rules or bounties are saved. */
    private static volatile int saveGeneration = 0;

    /** Rules sorted descending by thresholdMin (highest threshold first). */
    private List<BuyRateRule> rules = new ArrayList<>();

    /** Bounty map keyed by card name upper-cased. */
    private Map<String, BountyCard> bounties = new HashMap<>();

    /**
     * Creates the service and loads persisted rules/bounties immediately.
     */
    private final RateConfigurationRepository repository;
    private static final java.util.concurrent.ConcurrentMap<java.nio.file.Path,String> PUBLISHED=new java.util.concurrent.ConcurrentHashMap<>();
    public synchronized void refreshFromPublished() {
        String encoded=PUBLISHED.get(repository.path());
        if (encoded==null) return;
        JSONObject data=new JSONObject(encoded);
        if (data.optString("revision").equals(revision)) return;
        rules=buildDescendingList(parseRulesJson(data.getJSONArray("rules")));
        bounties=buildBountyMap(parseBountiesJson(data.optJSONArray("bounties")));
        revision=data.optString("revision");
    }
    private final boolean sharedEnabled;
    private String revision="";
    public synchronized String getRevision() { return revision.isEmpty() ? "defaults-50-40-v1" : revision; }
    private volatile String syncStatus="saved locally";
    public String getSyncStatus() { return syncStatus; }
    public BuyRateService() {
        sharedEnabled=true;
        repository=new RateConfigurationRepository(localConfigFile().toPath());
        try {
            if (repository.read().isEmpty()) {
                String oldRules=preferences().get("buy.rate.rules", "");
                if (!oldRules.isBlank()) {
                    JSONObject legacy=new JSONObject().put("rules",new JSONArray(oldRules))
                            .put("bounties",new JSONArray(preferences().get("buy.rate.bounties","[]")));
                    RateConfigurationRepository.validate(legacy); repository.save("",legacy);
                }
            }
        } catch (IOException e) { throw new java.io.UncheckedIOException("Legacy rates could not be migrated",e); }
        reload();
        TaskCoordinator.submit(this::pollSharedFolder);
    }
    public BuyRateService(java.nio.file.Path config) {
        sharedEnabled=false;
        repository=new RateConfigurationRepository(config); reload();
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
    public synchronized PayoutResult computePayout(String setCode, String collectorNumber,
                                      String cardName, BigDecimal marketValue) {
        // 1. Bounty lookup by card name
        if (cardName != null && !cardName.isEmpty()) {
            BountyCard bounty = bounties.get(cardName.toUpperCase(java.util.Locale.ROOT));
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

    /** Reloads the validated local document without consulting the network share. */
    public synchronized void reload() {
        try {
            JSONObject data=repository.read();
            if (data.isEmpty()) {
                rules=new ArrayList<>(List.of(new BuyRateRule(DEFAULT_THRESHOLD,DEFAULT_CREDIT,DEFAULT_CHECK)));
                bounties=new HashMap<>(); revision=""; return;
            }
            RateConfigurationRepository.validate(data);
            PUBLISHED.put(repository.path(),data.toString());
            rules=buildDescendingList(parseRulesJson(data.getJSONArray("rules")));
            bounties=buildBountyMap(parseBountiesJson(data.optJSONArray("bounties")));
            revision=data.optString("revision");
            syncStatus=data.optBoolean("pending") ? "pending sync" : "saved locally";
        } catch (IOException e) { throw new java.io.UncheckedIOException("Could not load buy rates",e); }
    }

    /** Background revision-checked sync. Publishes snapshots without replacing an open editor. */
    public void pollSharedFolder() {
        if (!sharedEnabled) return;
        String path=preferences().get("shared.trades.folder", "");
        if (path.isBlank()) return;
        try {
            syncStatus=repository.sync(java.nio.file.Path.of(path).resolve(SHARED_FILE));
            JSONObject data=repository.read();
            if (!data.isEmpty()) { RateConfigurationRepository.validate(data); PUBLISHED.put(repository.path(),data.toString()); saveGeneration++; }
        }
        catch (Exception e) { syncStatus="pending sync: "+e.getMessage(); }
    }

    public RateConfigurationRepository.Review reviewSharedRates() throws IOException {
        String path = preferences().get("shared.trades.folder", "");
        if (path.isBlank()) throw new IOException("Set a shared trades folder before reviewing shared rates.");
        return repository.review(java.nio.file.Path.of(path).resolve(SHARED_FILE));
    }
    public void resolveSharedRates(RateConfigurationRepository.Review review, RateConfigurationRepository.Choice choice) throws IOException {
        repository.resolve(review, choice);
        reload();
        syncStatus = "synced";
        saveGeneration++;
    }

    /**
     * Returns the current rules list in ascending threshold order (suitable for table display).
     *
     * @return list of rules ascending by threshold
     */
    public synchronized List<BuyRateRule> getRules() {
        List<BuyRateRule> ascending = new ArrayList<>(rules);
        ascending.sort(Comparator.comparing(r -> r.thresholdMin));
        return ascending;
    }

    /**
     * Returns the current bounties list sorted by card name.
     *
     * @return list of all bounty cards sorted alphabetically by name
     */
    public synchronized List<BountyCard> getBounties() {
        List<BountyCard> list = new ArrayList<>(bounties.values());
        list.sort(Comparator.comparing(b -> b.cardName.toUpperCase(java.util.Locale.ROOT)));
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
    public synchronized void saveRules(List<BuyRateRule> newRules) {
        boolean hasCatchAll = newRules.stream()
                .anyMatch(r -> r.thresholdMin.compareTo(BigDecimal.ZERO) == 0);
        if (!hasCatchAll) {
            throw new IllegalArgumentException(
                    "Rules must include a catch-all row with Min Price = $0.00.");
        }

        if (newRules.stream().map(r -> r.thresholdMin.stripTrailingZeros()).distinct().count() != newRules.size())
            throw new IllegalArgumentException("Duplicate price thresholds are not allowed");
        writeLocalFile(newRules, bounties.values());
        rules = buildDescendingList(newRules);
        saveGeneration++;
        writeToSharedFolder();
    }

    /**
     * Saves bounty cards to preferences and increments the save generation.
     *
     * @param newBounties bounties to persist
     */
    public synchronized void saveBounties(List<BountyCard> newBounties) {
        writeLocalFile(rules, newBounties);
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
        List<BountyCard> result=new ArrayList<>();
        boolean preserveSemicolons=false;
        try (var reader=Files.newBufferedReader(file.toPath(),java.nio.charset.StandardCharsets.UTF_8);
             var parser=org.apache.commons.csv.CSVFormat.DEFAULT.builder().setCommentMarker('#').setIgnoreEmptyLines(true).get().parse(reader)) {
            for (var row:parser) {
                if (row.getRecordNumber()==1 && "OCC bounty CSV v2".equals(row.getComment())) preserveSemicolons=true;
                if (row.getRecordNumber()==1 && row.get(0).trim().equalsIgnoreCase("CARD NAME")) continue;
                if (row.size()!=3) throw new IOException("Expected three columns at record "+row.getRecordNumber());
                // Older exports replaced commas with semicolons. Versioned exports preserve punctuation exactly.
                String name=row.get(0).trim();
                if (!preserveSemicolons) name=name.replace(';',',');
                try { result.add(new BountyCard(name,new BigDecimal(row.get(1).trim()).movePointLeft(2),new BigDecimal(row.get(2).trim()).movePointLeft(2))); }
                catch (IllegalArgumentException e) { throw new IOException("Invalid bounty at record "+row.getRecordNumber(),e); }
            }
        }
        return result;
    }

    /** Writes quoted CSV without changing card names; the marker distinguishes it from legacy exports. */
    public void exportBountyCsv(File file, List<BountyCard> bounties) throws IOException {
        var text=new java.io.StringWriter();
        var format=org.apache.commons.csv.CSVFormat.DEFAULT.builder().setCommentMarker('#').get();
        try (var csv=new org.apache.commons.csv.CSVPrinter(text,format)) {
            csv.printComment("OCC bounty CSV v2");
            csv.printRecord("CARD NAME","CREDIT PERCENT","CHECK PERCENT");
            for (BountyCard bounty:bounties) {
                csv.printRecord(bounty.cardName,bounty.creditRate.movePointRight(2).toPlainString(),
                        bounty.checkRate.movePointRight(2).toPlainString());
            }
        }
        com.cardpricer.util.AtomicFiles.write(file.toPath(),text.toString());
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
    // Private helpers — local config file
    // -------------------------------------------------------------------------

    private static File localConfigFile() {
        return new File(AppDataDirectory.config(), LOCAL_CONFIG_FILE);
    }

    /** Writes rules and bounties together to the local JSON config file. */
    private void writeLocalFile(Iterable<BuyRateRule> ruleList, Iterable<BountyCard> bountyList) {
        try {
            JSONObject data=new JSONObject().put("rules",buildRulesJson(ruleList)).put("bounties",buildBountiesJson(bountyList));
            RateConfigurationRepository.validate(data);
            JSONObject saved=repository.save(revision,data); revision=saved.getString("revision"); syncStatus="pending sync";
            PUBLISHED.put(repository.path(),saved.toString());
        } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private void writeToSharedFolder() {
        TaskCoordinator.submit(this::pollSharedFolder);
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
                        obj.getBigDecimal("thresholdMin"),
                        obj.getBigDecimal("creditRate"),
                        obj.getBigDecimal("checkRate")));
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
                        obj.getBigDecimal("creditRate"),
                        obj.getBigDecimal("checkRate")));
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
