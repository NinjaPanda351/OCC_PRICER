package com.cardpricer.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Contains the list of Magic: The Gathering set codes for bulk processing.
 *
 * <p>The built-in codes are hardcoded below.  Users may add extra sets via
 * Preferences → Custom Sets; those are persisted to {@link Preferences} and
 * merged into {@link #ALL_SETS_CUSTOM_CODES} at startup.
 */
public class SetList {

    // ── Built-in set codes (hardcoded, never modified at runtime) ─────────────

    private static final List<String> BUILTIN_SETS = List.of(
            "3ED", "4ED", "5ED", "6ED", "7ED", "8ED", "9ED", "XED",
            "M10", "M11", "M12", "M13", "M14", "M15",
            "AFR", "AFC",
            "DFT", "DRC",
            "AER", "ARB", "ALL", "AKH", "APC", "ARC", "E01", "AVR",
            "BFZ", "BRB", "BBD", "BTD", "BOK",
            "BLB", "BLC",
            "BNG", "COK", "CHR", "CSP",
            "CMD", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20", "C21",
            "CMA", "CM2", "CM1", "CC2", "CC1",
            "CMR", "CLB", "CMM",
            "CON", "CNS", "CN2",
            "M19", "M20", "M21",
            "DKA", "DST", "DKM", "DIS",
            "DOM", "DMR", "DMU", "DMC",
            "2XM", "2X2",
            "DGM", "DTK",
            "AVNB", "DDQ", "DVD", "DDO", "EVT", "EVG", "DDU", "GVL", "HVM",
            "IVG", "JVC", "JVV", "KVD", "DDT", "DDS", "DDR", "PVC", "SVT",
            "DDN", "VVK", "DDP", "DPW",
            "DSK", "DSC",
            "ECL", "ECC", "EOE", "EOC", "EOS",
            "EMN", "EMA", "EVE", "EXO", "E02",
            "FEM", "FRF", "5DN",
            "FIN", "FIC", "FCA",
            "FDN", "FDC", "J25",
            "V15", "V14", "V08", "V09", "V11", "V16", "V12", "V10", "V17", "V20",
            "FUT",
            "GNT", "GN2", "GN3", "GTC", "GS1", "GPT",
            "GRN", "GK1",
            "HML", "HOU", "ICE", "IMA",
            "IKO",
            "ISD",
            "MID", "MIC",
            "VOW", "VOC", "DBL",
            "INR",
            "INV", "XLN", "JOU", "JUD", "JMP", "J22",
            "KLD",
            "KHM", "KHC",
            "NEO", "NEC",
            "KTK", "LEG", "LGN", "LRW", "ORI",
            "MOM", "MOC", "MAT",
            "MAR", "SPM", "SPE",
            "EXP", "MPS", "MP2", "MED",
            "M25", "MMQ", "MIR", "MRD", "MBS", "MD1",
            "MH1", "MHR", "MH2", "MH3", "M3C",
            "MMA", "MM2", "MM3",
            "MOR", "MUL",
            "MKM", "MKC",
            "MB2", "NEM", "NPH", "OGW", "ODY", "ONS",
            "OTJ", "OTP", "OTC", "BIG",
            "ONE", "ONC",
            "PLC", "PC", "PC2", "PCA", "PLS",
            "POR", "PTK", "P02",
            "PFI", "PGB", "SLI", "PCY",
            "RAV", "RNA", "GK2", "CLU", "RVR", "RTR",
            "ROE", "RIX", "SOK", "SOM", "SCG", "SLD",
            "SHM", "SOI", "ALA",
            "SS3", "SS2", "SS1", "SPG",
            "S99", "S00", "SCD",
            "SNC", "NCC",
            "STA", "STX",
            "STH",
            "TDM", "TDC",
            "TMP",
            "BRO", "BRR", "BRC",
            "LTR", "LTC",
            "LCI", "LCC",
            "THS", "THB",
            "ELD",
            "TLA", "TLE", "TMT", "TMC", "PZA", "TSP", "TSR", "TSTS", "TOR",
            "UMA",
            "UNF",
            "ACR", "WHO", "PIP", "REX", "BOT", "40K",
            "2ED", "UDS", "ULG", "USG", "VIS",
            "WAR", "WTH",
            "WOE", "WOC", "WOT",
            "WWK", "ZEN", "ZNR", "ZNC", "ZNE"
    );

    // ── Dynamic combined list (builtins + user-added) ─────────────────────────

    /**
     * All set codes — built-in plus any user-added sets.
     * Rebuilt whenever custom sets are saved via the UI.
     */
    public static final List<String> ALL_SETS_CUSTOM_CODES = new ArrayList<>(BUILTIN_SETS);

    // ── Built-in code mappings (custom code ↔ Scryfall code) ─────────────────

    private static final Map<String, String> CUSTOM_TO_API = new HashMap<>();
    private static final Map<String, String> API_TO_CUSTOM = new HashMap<>();

    // ── User-added code mappings (separate map, rebuilt on save) ─────────────

    private static final Map<String, String> USER_CUSTOM_TO_API = new HashMap<>();
    private static final Map<String, String> USER_API_TO_CUSTOM = new HashMap<>();

    // ── Persistence ───────────────────────────────────────────────────────────

    private static final String CUSTOM_SETS_FILE = "custom_sets.json";

    /** Legacy Preferences node — used only for one-time migration. */
    private static final Preferences LEGACY_PREFS = Preferences.userNodeForPackage(SetList.class);
    private static final String LEGACY_KEY        = "user.custom.sets";

    // ── Static initialisation ─────────────────────────────────────────────────

    static {
        addBuiltinMapping("XED",  "10e");
        addBuiltinMapping("COK",  "chk");
        addBuiltinMapping("AVNB", "ddh");
        addBuiltinMapping("EVT",  "ddf");
        addBuiltinMapping("HVM",  "ddl");
        addBuiltinMapping("IVG",  "ddj");
        addBuiltinMapping("JVC",  "dd2");
        addBuiltinMapping("JVV",  "ddm");
        addBuiltinMapping("KVD",  "ddg");
        addBuiltinMapping("PVC",  "dde");
        addBuiltinMapping("SVT",  "ddk");
        addBuiltinMapping("VVK",  "ddi");
        addBuiltinMapping("DPW",  "dpa");
        addBuiltinMapping("V08",  "drb");
        addBuiltinMapping("V20",  "v13");
        addBuiltinMapping("M25",  "a25");
        addBuiltinMapping("MHR",  "h1r");
        addBuiltinMapping("PC",   "hop");
        addBuiltinMapping("PFI",  "pd2");
        addBuiltinMapping("PGB",  "pd3");
        addBuiltinMapping("SLI",  "h09");
        addBuiltinMapping("TSTS", "tsb");

        loadCustomSets();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Converts a custom display code to the Scryfall API code.
     * Falls back to lowercase of the custom code if no mapping exists.
     */
    public static String toScryfallCode(String customCode) {
        if (customCode == null) return null;
        String upper = customCode.toUpperCase();
        String builtin = CUSTOM_TO_API.get(upper);
        if (builtin != null) return builtin;
        String user = USER_CUSTOM_TO_API.get(upper);
        return user != null ? user : upper.toLowerCase();
    }

    /**
     * Converts a Scryfall API code to the custom display code.
     * Falls back to uppercase of the Scryfall code if no mapping exists.
     */
    public static String fromScryfallCode(String scryfallCode) {
        if (scryfallCode == null) return null;
        String lower = scryfallCode.toLowerCase();
        String builtin = API_TO_CUSTOM.get(lower);
        if (builtin != null) return builtin;
        String user = USER_API_TO_CUSTOM.get(lower);
        return user != null ? user : scryfallCode.toUpperCase();
    }

    /**
     * Returns the user-added sets as a list of {@code [customCode, scryfallCode]} pairs.
     * {@code scryfallCode} is empty string when no custom mapping is needed.
     * Used by the Custom Sets UI to populate its table.
     */
    public static List<String[]> getCustomSetEntries() {
        List<String[]> entries = new ArrayList<>();
        String json = readCustomSetsJson();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String c = obj.getString("c").toUpperCase();
                String s = obj.optString("s", "");
                entries.add(new String[]{c, s});
            }
        } catch (Exception ignored) {}
        return entries;
    }

    /**
     * Persists the given custom set entries and immediately updates the in-memory
     * lists and maps.  Call this from the UI after the user clicks Save.
     *
     * @param entries list of {@code [customCode, scryfallCode]} pairs;
     *                {@code scryfallCode} may be null or empty when not needed
     */
    public static void saveCustomSets(List<String[]> entries) {
        JSONArray arr = new JSONArray();
        for (String[] e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("c", e[0].toUpperCase().trim());
            if (e.length > 1 && e[1] != null && !e[1].isBlank()) {
                obj.put("s", e[1].toLowerCase().trim());
            }
            arr.put(obj);
        }
        writeCustomSetsJson(arr.toString());
        applyCustomSets(entries);
    }

    /** Returns the total number of set codes (built-in + custom). */
    public static int getSetCount() {
        return ALL_SETS_CUSTOM_CODES.size();
    }

    /** Returns {@code true} if the code is in the combined set list. */
    public static boolean isValidSetCode(String setCode) {
        return ALL_SETS_CUSTOM_CODES.contains(setCode.toUpperCase());
    }

    /** Returns {@code true} if the code is a built-in (non-user-added) set. */
    public static boolean isBuiltinSet(String setCode) {
        return BUILTIN_SETS.contains(setCode.toUpperCase());
    }

    /** @deprecated Use {@link #toScryfallCode(String)} instead */
    @Deprecated
    public static String getApiCode(String setCode) {
        return toScryfallCode(setCode);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Reads custom sets JSON, with fallback chain:
     * 1. configDir() / custom_sets.json  (shared or local)
     * 2. local AppData config            (when shared dir differs)
     * 3. legacy Preferences key          (one-time migration)
     */
    private static String readCustomSetsJson() {
        File primary = new File(SharedFolderLocator.configDir(), CUSTOM_SETS_FILE);
        if (primary.exists()) {
            try { return Files.readString(primary.toPath()); }
            catch (IOException e) {
                System.err.println("[SetList] Cannot read " + primary + ": " + e.getMessage());
            }
        }

        // Fallback: local AppData config
        File local = new File(AppDataDirectory.config(), CUSTOM_SETS_FILE);
        if (local.exists() && !local.getAbsolutePath().equals(primary.getAbsolutePath())) {
            try { return Files.readString(local.toPath()); }
            catch (IOException e) {
                System.err.println("[SetList] Cannot read " + local + ": " + e.getMessage());
            }
        }

        // Last resort: legacy Preferences migration
        String fromPrefs = LEGACY_PREFS.get(LEGACY_KEY, "[]");
        if (!"[]".equals(fromPrefs) && !fromPrefs.isBlank()) {
            System.out.println("[SetList] Migrating custom sets from Preferences → " + primary);
            writeCustomSetsJson(fromPrefs);
            LEGACY_PREFS.remove(LEGACY_KEY);
        }
        return fromPrefs;
    }

    private static void writeCustomSetsJson(String json) {
        File f = new File(SharedFolderLocator.configDir(), CUSTOM_SETS_FILE);
        try {
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), json);
        } catch (IOException e) {
            System.err.println("[SetList] Cannot write " + f + ": " + e.getMessage());
        }
    }

    private static void addBuiltinMapping(String customCode, String apiCode) {
        CUSTOM_TO_API.put(customCode.toUpperCase(), apiCode.toLowerCase());
        API_TO_CUSTOM.put(apiCode.toLowerCase(), customCode.toUpperCase());
    }

    private static void loadCustomSets() {
        applyCustomSets(getCustomSetEntries());
    }

    /** Rebuilds {@link #ALL_SETS_CUSTOM_CODES} and user mapping maps from {@code entries}. */
    private static void applyCustomSets(List<String[]> entries) {
        USER_CUSTOM_TO_API.clear();
        USER_API_TO_CUSTOM.clear();

        ALL_SETS_CUSTOM_CODES.clear();
        ALL_SETS_CUSTOM_CODES.addAll(BUILTIN_SETS);

        for (String[] e : entries) {
            String customCode = e[0].toUpperCase().trim();
            if (customCode.isEmpty()) continue;
            if (!ALL_SETS_CUSTOM_CODES.contains(customCode)) {
                ALL_SETS_CUSTOM_CODES.add(customCode);
            }
            if (e.length > 1 && e[1] != null && !e[1].isBlank()) {
                String scryfallCode = e[1].toLowerCase().trim();
                USER_CUSTOM_TO_API.put(customCode, scryfallCode);
                USER_API_TO_CUSTOM.put(scryfallCode, customCode);
            }
        }
    }
}
