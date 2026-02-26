package com.cardpricer.util;

import com.cardpricer.model.ParsedCode;

/** Static utility for parsing and formatting user-typed card codes. */
public final class CardCodeParser {

    private CardCodeParser() {}

    /**
     * Parses input like "TDM 3", "TDM 3f", or "TDM 3e" into a ParsedCode.
     * Returns {@code null} if the input cannot be parsed.
     */
    public static ParsedCode parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        input = input.trim().toUpperCase().replaceAll("\\s+", " ");

        // Check for finish indicators - handle both "1f" and "1 f" formats
        String finish = "";

        // Check if ends with 'F', 'E', or 'S' (already uppercased)
        boolean isSurge = false;
        if (input.endsWith("F")) {
            finish = "F";
            input = input.substring(0, input.length() - 1).trim();
        } else if (input.endsWith("E")) {
            finish = "E";
            input = input.substring(0, input.length() - 1).trim();
        } else if (input.endsWith("S")) {
            finish = "S";
            isSurge = true;
            input = input.substring(0, input.length() - 1).trim();
        }

        String[] parts = input.split(" ");
        if (parts.length < 2) {
            return null;
        }

        // PLST (The List) detection
        // Accepted formats:
        //   "PLST ARB 1"   → 3 tokens (set + number space-separated)
        //   "PLST PLS11"   → 2 tokens, second is letters+digits concatenated
        //   "PLST arb-1"   → 2 tokens, re-entry of a previously formatted code
        if ("PLST".equals(parts[0])) {
            // Input is already uppercased by this point; preserve case so the
            // Scryfall URL matches exactly: /cards/plst/PLS-11
            if (parts.length >= 3) {
                // "PLST PLS 11" or "PLST ARB 1"
                StringBuilder collNum = new StringBuilder();
                for (int i = 2; i < parts.length; i++) {
                    collNum.append(parts[i]);
                }
                return new ParsedCode("plst", parts[1] + "-" + collNum, finish);
            } else if (parts.length == 2) {
                String combined = parts[1];
                if (combined.contains("-")) {
                    // Already in canonical form: "PLST PLS-11"
                    return new ParsedCode("plst", combined, finish);
                }
                // Try to split concatenated "PLS11" → "PLS-11"
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("^([A-Za-z]+)(\\d+[A-Za-z]*)$").matcher(combined);
                if (m.matches()) {
                    return new ParsedCode("plst", m.group(1) + "-" + m.group(2), finish);
                }
            }
        }

        String setCode = parts[0].toUpperCase();

        // Collector number may have letter suffixes (e.g., "143b", "5a")
        StringBuilder collectorNumber = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            collectorNumber.append(parts[i]);
        }

        // Handle case where user types without space (e.g., "CHR143B")
        if (parts.length == 1 && parts[0].length() > 3) {
            String combined = parts[0];
            if (combined.length() >= 4) {
                setCode = combined.substring(0, 3);
                collectorNumber = new StringBuilder(combined.substring(3));
            }
        }

        // Apply set conversion mapping (e.g., CHK -> chk for Scryfall API)
        String apiSetCode = SetList.toScryfallCode(setCode);

        // Scryfall expects lowercase collector numbers with letter suffixes
        String apiCollectorNumber = collectorNumber.toString().toLowerCase();

        // Surge foil: Scryfall uses ★ (U+2605) appended to the collector number (e.g. "73★")
        if (isSurge) apiCollectorNumber = apiCollectorNumber + "★";

        return new ParsedCode(apiSetCode, apiCollectorNumber, finish);
    }

    /** Formats a ParsedCode back into a canonical display string. */
    public static String format(ParsedCode code) {
        StringBuilder formatted = new StringBuilder();

        if (code.setCode.equalsIgnoreCase("plst")) {
            // PLST: "PLST arb-1" — hyphen preserved; strip any Scryfall API markers (★)
            String collNum = code.collectorNumber.replace("★", "");
            formatted.append("PLST ").append(collNum);
        } else {
            // Strip Scryfall API markers (★ for surge foil) from the display number
            String displayNum = code.collectorNumber.replace("★", "");
            formatted.append(code.setCode.toUpperCase())
                    .append(" ")
                    .append(displayNum);
        }

        if (!code.finish.isEmpty()) {
            formatted.append(code.finish.toLowerCase()); // Lowercase, no space
        }

        return formatted.toString();
    }

    /** Capitalizes the first letter of a string. */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
