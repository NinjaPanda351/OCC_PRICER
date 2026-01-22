package com.cardpricer.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains hardcoded list of Magic: The Gathering set codes
 * for bulk processing operations.
 */
public class SetList {

    /**
     * All set codes using OUR CUSTOM CODES (what we display and save as)
     */
    public static final List<String> ALL_SETS_CUSTOM_CODES = List.of(
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
            "TLA", "TLE", "TSP", "TSR", "TSTS", "TOR",
            "UMA",
            "UNF",
            "ACR", "WHO", "PIP", "REX", "BOT", "40K",
            "2ED", "UDS", "ULG", "USG", "VIS",
            "WAR", "WTH",
            "WOE", "WOC", "WOT",
            "WWK", "ZEN", "ZNR", "ZNC", "ZNE"
    );

    /**
     * Map: OUR CUSTOM CODE -> SCRYFALL API CODE
     * Use this when making API calls
     */
    private static final Map<String, String> CUSTOM_TO_API = new HashMap<>();

    /**
     * Map: SCRYFALL API CODE -> OUR CUSTOM CODE
     * Use this when parsing API responses
     */
    private static final Map<String, String> API_TO_CUSTOM = new HashMap<>();

    static {
        // Initialize the bidirectional mapping
        addMapping("XED", "10e");
        addMapping("COK", "chk");
        addMapping("AVNB", "ddh");
        addMapping("EVT", "ddf");
        addMapping("HVM", "ddl");
        addMapping("IVG", "ddj");
        addMapping("JVC", "dd2");
        addMapping("JVV", "ddm");
        addMapping("KVD", "ddg");
        addMapping("PVC", "dde");
        addMapping("SVT", "ddk");
        addMapping("VVK", "ddi");
        addMapping("DPW", "dpa");
        addMapping("V08", "drb");
        addMapping("V20", "v13");
        addMapping("M25", "a25");
        addMapping("MHR", "h1r");
        addMapping("PC", "hop");
        addMapping("PFI", "pd2");
        addMapping("PGB", "pd3");
        addMapping("SLI", "h09");
        addMapping("TSTS", "tsb");
    }

    /**
     * Helper to add bidirectional mapping
     */
    private static void addMapping(String customCode, String apiCode) {
        CUSTOM_TO_API.put(customCode.toUpperCase(), apiCode.toLowerCase());
        API_TO_CUSTOM.put(apiCode.toLowerCase(), customCode.toUpperCase());
    }

    /**
     * Converts OUR custom code to Scryfall API code for making API calls
     * Example: "COK" -> "chk"
     *
     * @param customCode Our internal set code
     * @return Scryfall API code (lowercase)
     */
    public static String toScryfallCode(String customCode) {
        if (customCode == null) {
            return null;
        }
        String upperCode = customCode.toUpperCase();
        return CUSTOM_TO_API.getOrDefault(upperCode, upperCode.toLowerCase());
    }

    /**
     * Converts Scryfall API code to OUR custom code for display/storage
     * Example: "chk" -> "COK"
     *
     * @param scryfallCode The code from Scryfall API response
     * @return Our custom code (uppercase)
     */
    public static String fromScryfallCode(String scryfallCode) {
        if (scryfallCode == null) {
            return null;
        }
        String lowerCode = scryfallCode.toLowerCase();
        return API_TO_CUSTOM.getOrDefault(lowerCode, scryfallCode.toUpperCase());
    }

    /**
     * @deprecated Use toScryfallCode() instead
     */
    @Deprecated
    public static String getApiCode(String setCode) {
        return toScryfallCode(setCode);
    }

    /**
     * Returns the total number of unique set codes
     */
    public static int getSetCount() {
        return ALL_SETS_CUSTOM_CODES.size();
    }

    /**
     * Checks if a set code exists in our custom code list
     */
    public static boolean isValidSetCode(String setCode) {
        return ALL_SETS_CUSTOM_CODES.contains(setCode.toUpperCase());
    }
}