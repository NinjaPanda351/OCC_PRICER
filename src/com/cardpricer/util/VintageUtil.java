package com.cardpricer.util;

import java.math.BigDecimal;
import java.util.*;

/**
 * Utilities for recognising and handling vintage Magic: The Gathering sets.
 *
 * <p>Covers:
 * <ul>
 *   <li>Resolving friendly set-name aliases to Scryfall codes (e.g. "alpha" → "lea")</li>
 *   <li>Identifying vintage sets (pre-Mirrodin era, up to and including 8th Edition)</li>
 *   <li>High-value card confirmation threshold</li>
 *   <li>Vintage set reference table data for {@link com.cardpricer.gui.ShortcutHelpDialog}</li>
 * </ul>
 */
public final class VintageUtil {

    /**
     * Cards priced at or above this threshold will trigger a confirmation dialog
     * before being added to a trade.
     */
    public static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("100.00");

    /** Scryfall set codes (lowercase) considered vintage for auto-image-popup purposes. */
    private static final Set<String> VINTAGE_SETS;

    /** Friendly name aliases → Scryfall set code (lowercase). */
    private static final Map<String, String> SET_ALIASES;

    static {
        VINTAGE_SETS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                "lea", "leb", "2ed", "arn", "atq", "leg", "drk",
                "3ed", "fem", "4ed", "ice", "hml", "all", "chr",
                "mir", "vis", "wth", "por", "p02", "ptk",
                "tmp", "sth", "exo",
                "usg", "ulg", "uds",
                "mmq", "nem", "pcy",
                "inv", "pls", "apc",
                "ody", "tor", "jud",
                "ons", "lgn", "scg",
                "5ed", "6ed", "7ed", "8ed"
        )));

        Map<String, String> m = new HashMap<>();
        m.put("alpha",               "lea");
        m.put("limitededitionalpha", "lea");
        m.put("beta",                "leb");
        m.put("limitededitionbeta",  "leb");
        m.put("unlimited",           "2ed");
        m.put("unlimitededition",    "2ed");
        m.put("revised",             "3ed");
        m.put("revisededition",      "3ed");
        m.put("fourth",              "4ed");
        m.put("fourthedition",       "4ed");
        m.put("fifth",               "5ed");
        m.put("fifthedition",        "5ed");
        m.put("sixth",               "6ed");
        m.put("sixthedition",        "6ed");
        m.put("seventh",             "7ed");
        m.put("seventhedition",      "7ed");
        m.put("eighth",              "8ed");
        m.put("eighthedition",       "8ed");
        m.put("arabian",             "arn");
        m.put("arabiannights",       "arn");
        m.put("antiquities",         "atq");
        m.put("legends",             "leg");
        m.put("thedark",             "drk");
        m.put("dark",                "drk");
        m.put("fallen",              "fem");
        m.put("fallenempires",       "fem");
        m.put("homelands",           "hml");
        m.put("iceage",              "ice");
        m.put("alliances",           "all");
        m.put("chronicles",          "chr");
        m.put("mirage",              "mir");
        m.put("visions",             "vis");
        m.put("weatherlight",        "wth");
        m.put("portal",              "por");
        m.put("portalsecondage",     "p02");
        m.put("portalsecond",        "p02");
        m.put("portalthreekingdoms", "ptk");
        m.put("tempest",             "tmp");
        m.put("stronghold",          "sth");
        m.put("exodus",              "exo");
        m.put("urzassaga",           "usg");
        m.put("urzasaga",            "usg");
        m.put("urza",                "usg");
        m.put("urzaslegacy",         "ulg");
        m.put("urzadestiny",         "uds");
        m.put("urzasdestiny",        "uds");
        m.put("masques",             "mmq");
        m.put("mercadianmasques",    "mmq");
        m.put("nemesis",             "nem");
        m.put("prophecy",            "pcy");
        m.put("invasion",            "inv");
        m.put("planeshift",          "pls");
        m.put("apocalypse",          "apc");
        m.put("odyssey",             "ody");
        m.put("torment",             "tor");
        m.put("judgment",            "jud");
        m.put("onslaught",           "ons");
        m.put("legions",             "lgn");
        m.put("scourge",             "scg");
        SET_ALIASES = Collections.unmodifiableMap(m);
    }

    private VintageUtil() {}

    /**
     * Resolves a friendly set name or alias to a lowercase Scryfall set code.
     *
     * <p>Examples: {@code "alpha"} → {@code "lea"},
     * {@code "Arabian Nights"} → {@code "arn"}.
     * Returns the input lowercased if no alias is found (pass-through for real codes).
     *
     * @param input user-supplied set name or code; may be mixed case or contain spaces
     * @return Scryfall set code (lowercase)
     */
    public static String resolveSetAlias(String input) {
        if (input == null || input.isBlank()) return input;
        String normalized = input.toLowerCase().replaceAll("[^a-z0-9]", "");
        return SET_ALIASES.getOrDefault(normalized, input.toLowerCase());
    }

    /**
     * Returns {@code true} if the given set code (any case) belongs to the vintage
     * set list (roughly pre-Mirrodin era, up to and including 8th Edition).
     *
     * @param setCode Scryfall set code or our custom code — any case
     */
    public static boolean isVintageSet(String setCode) {
        if (setCode == null) return false;
        return VINTAGE_SETS.contains(setCode.toLowerCase());
    }

    // ── Vintage set reference dialog data ────────────────────────────────────

    /** Column headers for the vintage set reference dialog. */
    public static final String[] REF_COLUMNS = {"Type Code", "Full Set Name"};

    /**
     * Row data for the vintage set reference dialog, compatible with
     * {@link com.cardpricer.gui.ShortcutHelpDialog}.
     * Rows whose first cell starts with {@code "---"} render as section dividers.
     */
    public static final String[][] REF_ROWS = {
        {"--- Black Border Sets",                      ""},
        {"LEA",         "Limited Edition Alpha"},
        {"LEB",         "Limited Edition Beta"},
        {"2ED",         "Unlimited Edition"},
        {"ARN",         "Arabian Nights"},
        {"ATQ",         "Antiquities"},
        {"LEG",         "Legends"},
        {"DRK",         "The Dark"},
        {"--- White Border Sets",                      ""},
        {"3ED",         "Revised Edition"},
        {"FEM",         "Fallen Empires"},
        {"4ED",         "Fourth Edition"},
        {"ICE",         "Ice Age"},
        {"HML",         "Homelands"},
        {"ALL",         "Alliances"},
        {"CHR",         "Chronicles"},
        {"5ED",         "Fifth Edition"},
        {"--- Mirage Block",                           ""},
        {"MIR",         "Mirage"},
        {"VIS",         "Visions"},
        {"WTH",         "Weatherlight"},
        {"--- Portal Sets",                            ""},
        {"POR",         "Portal"},
        {"P02",         "Portal: Second Age"},
        {"PTK",         "Portal: Three Kingdoms"},
        {"--- Tempest Block",                          ""},
        {"TMP",         "Tempest"},
        {"STH",         "Stronghold"},
        {"EXO",         "Exodus"},
        {"--- Urza Block",                             ""},
        {"USG",         "Urza's Saga"},
        {"ULG",         "Urza's Legacy"},
        {"UDS",         "Urza's Destiny"},
        {"--- Masques Block",                          ""},
        {"MMQ",         "Mercadian Masques"},
        {"NEM",         "Nemesis"},
        {"PCY",         "Prophecy"},
        {"--- Invasion Block",                         ""},
        {"INV",         "Invasion"},
        {"PLS",         "Planeshift"},
        {"APC",         "Apocalypse"},
        {"--- Odyssey Block",                          ""},
        {"ODY",         "Odyssey"},
        {"TOR",         "Torment"},
        {"JUD",         "Judgment"},
        {"--- Onslaught Block",                        ""},
        {"ONS",         "Onslaught"},
        {"LGN",         "Legions"},
        {"SCG",         "Scourge"},
        {"--- Core Sets (Vintage Era)",                ""},
        {"6ED",         "Sixth Edition"},
        {"7ED",         "Seventh Edition"},
        {"8ED",         "Eighth Edition"},
        {"--- Name Shortcuts  (type instead of code)", ""},
        {"alpha",       "→ LEA   (Limited Edition Alpha)"},
        {"beta",        "→ LEB   (Limited Edition Beta)"},
        {"unlimited",   "→ 2ED   (Unlimited Edition)"},
        {"revised",     "→ 3ED   (Revised Edition)"},
        {"arabian",     "→ ARN   (Arabian Nights)"},
        {"antiquities", "→ ATQ   (Antiquities)"},
        {"legends",     "→ LEG   (Legends)"},
        {"dark",        "→ DRK   (The Dark)"},
        {"tempest",     "→ TMP   (Tempest)"},
        {"masques",     "→ MMQ   (Mercadian Masques)"},
        {"invasion",    "→ INV   (Invasion)"},
        {"odyssey",     "→ ODY   (Odyssey)"},
        {"onslaught",   "→ ONS   (Onslaught)"},
    };
}
