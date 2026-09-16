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
        if (input==null || input.isBlank()) return null;
        String text=input.trim().replaceAll("\\s+", " ");
        if (text.matches("(?i)^[a-z]{3}\\d.*")) text=text.substring(0,3)+" "+text.substring(3);
        var match=java.util.regex.Pattern.compile("(?i)^([a-z0-9]{2,8}) (?:([a-z0-9]{2,8})[ -])?(\\d+[a-z0-9★†.*-]*)(?: ([fes]))?$").matcher(text);
        if (!match.matches()) return null;
        String set=match.group(1), prefix=match.group(2), number=match.group(3), finish=match.group(4);
        if (prefix!=null && !"plst".equalsIgnoreCase(set)) return null;
        if (finish==null) {
            finish="";
            if (number.matches("(?i).*\\d[fes]$")) { finish=number.substring(number.length()-1); number=number.substring(0,number.length()-1); }
        }
        if (prefix!=null) number=prefix.toUpperCase(java.util.Locale.ROOT)+"-"+number;
        else number=number.toLowerCase(java.util.Locale.ROOT);
        if ("s".equalsIgnoreCase(finish) && !number.endsWith("★")) number+="★";
        return new ParsedCode(SetList.toScryfallCode(set),number,finish.toUpperCase(java.util.Locale.ROOT));
    }

    /** Formats a ParsedCode back into a canonical display string. */
    public static String format(ParsedCode code) {
        StringBuilder formatted = new StringBuilder();

        if (code.setCode.equalsIgnoreCase("plst")) {
            // PLST: "PLST arb-1" — hyphen preserved; strip any Scryfall API markers (★)
            String collNum = code.collectorNumber;
            formatted.append("PLST ").append(collNum);
        } else {
            // Strip Scryfall API markers (★ for surge foil) from the display number
            String displayNum = code.collectorNumber;
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
