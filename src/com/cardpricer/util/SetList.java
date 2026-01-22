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
     * All set codes from the provided list
     * Note: Some sets share codes (variants use the same code as their base set)
     */
    public static final List<String> ALL_SETS = List.of(
            "3ED", "4ED", "5ED", "6ED", "7ED", "8ED", "9ED", "10E",
            "M10", "M11", "M12", "M13", "M14", "M15",
            "AFR", "AFC",
            "DFT", "DRC",
            "AER", "ARB", "ALL", "AKH", "APC", "ARC", "E01", "AVR",
            "BFZ", "BRB", "BBD", "BTD", "BOK",
            "BLB", "BLC",
            "BNG", "CHK", "CHR", "CSP",
            "CMD", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20", "C21",
            "CMA", "CM2", "CM1", "CC2", "CC1",
            "CMR", "CLB", "CMM",
            "CON", "CNS", "CN2",
            "M19", "M20", "M21",
            "DKA", "DST", "DKM", "DIS",
            "DOM", "DMR", "DMU", "DMC",
            "2XM", "2X2",
            "DGM", "DTK",
            "DDH", "DDQ", "DVD", "DDO", "DDF", "EVG", "DDU", "GVL", "DDL",
            "DDJ", "DD2", "DDM", "DDG", "DDT", "DDS", "DDR", "DDE", "DDK",
            "DDN", "DDI", "DDP", "DPA",
            "DSK", "DSC",
            "ECL", "ECC", "EOE", "EOC", "EOS",
            "EMN", "EMA", "EVE", "EXO", "E02",
            "FEM", "FRF", "5DN",
            "FIN", "FIC", "FCA",
            "FDN", "FDC", "J25",
            "V15", "V14", "DRB", "V09", "V11", "V16", "V12", "V10", "V17", "V13",
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
            "A25", "MMQ", "MIR", "MRD", "MBS", "MD1",
            "MH1", "H1R", "MH2", "MH3", "M3C",
            "MMA", "MM2", "MM3",
            "MOR", "MUL",
            "MKM", "MKC",
            "MB2", "NEM", "NPH", "OGW", "ODY", "ONS",
            "OTJ", "OTP", "OTC", "BIG",
            "ONE", "ONC",
            "PLC", "HOP", "PC2", "PCA", "PLS",
            "POR", "PTK", "P02",
            "PD2", "PD3", "H09", "PCY",
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
            "TLA", "TLE", "TSP", "TSR", "TSB", "TOR",
            "UMA",
            "UNF",
            "ACR", "WHO", "PIP", "REX", "BOT", "40K",
            "2ED", "UDS", "ULG", "USG", "VIS",
            "WAR", "WTH",
            "WOE", "WOC", "WOT",
            "WWK", "ZEN", "ZNR", "ZNC", "ZNE"
    );


    /**
     * All set codes, with originals updated with our usage
     * Note: Some sets share codes (variants use the same code as their base set)
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
     * Map of Scryfall API codes to our internal save codes
     * Key = Scryfall code (what Scryfall returns)
     * Value = Our code (what we save as)
     */
    private static final Map<String, String> API_MAPPING = new HashMap<>();

    static {
        // Initialize the mapping
        API_MAPPING.put("XED", "10E");
        API_MAPPING.put("COK", "CHK");
        API_MAPPING.put("AVNB", "DDH");
        API_MAPPING.put("EVT", "DDF");
        API_MAPPING.put("HVM", "DDL");
        API_MAPPING.put("IVG", "DDJ");
        API_MAPPING.put("JVC", "DD2");
        API_MAPPING.put("JVV", "DDM");
        API_MAPPING.put("KVD", "DDG");
        API_MAPPING.put("PVC", "DDE");
        API_MAPPING.put("SVT", "DDK");
        API_MAPPING.put("VVK", "DDI");
        API_MAPPING.put("DPW", "DPA");
        API_MAPPING.put("V08", "DRB");
        API_MAPPING.put("V20", "V13");
        API_MAPPING.put("M25", "A25");
        API_MAPPING.put("MHR", "H1R");
        API_MAPPING.put("PC", "HOP");
        API_MAPPING.put("PFI", "PD2");
        API_MAPPING.put("PGB", "PD3");
        API_MAPPING.put("SLI", "H09");
        API_MAPPING.put("TSTS", "TSB");
    }

    /**
     * Returns the total number of unique set codes
     */
    public static int getSetCount() {
        return ALL_SETS.size();
    }

    /**
     * Checks if a set code exists in the list
     */
    public static boolean isValidSetCode(String setCode) {
        return ALL_SETS.contains(setCode.toUpperCase());
    }

    /**
     * Gets the code to use when calling Scryfall API
     * Just returns the set code as-is since we're searching with standard codes
     * @param setCode The set code from our list
     * @return The code to use for API calls
     */
    public static String getApiCode(String setCode) {
        if (setCode == null) {
            return null;
        }
        String upperCode = setCode.toUpperCase();
        return API_MAPPING.getOrDefault(upperCode, upperCode);
    }
}