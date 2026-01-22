package com.cardpricer.model;

import java.math.BigDecimal;

/**
 * Represents a flattened card entry for CSV export.
 * Each finish variant (normal, foil, etc.) gets its own entry.
 *
 * Example:
 * - "TLA 1" for normal Aang's Journey
 * - "TLA 1F" for foil Aang's Journey
 */
public class CardEntry {
    private String mySetCollectorCode;  // "TLA 1" or "TLA 1F"
    private String myCardName;           // "Aang's Journey" or "Aang's Journey (Foil)"
    private BigDecimal myPrice;          // Single price for this variant
    private String myRarity;             // Rarity: common, uncommon, rare, mythic, etc.
    private String myArtist;             // Artist name

    /**
     * Creates a CardEntry from a Card object
     * @param theCard The source card
     * @param isFoil Whether this entry is for the foil version
     */
    public CardEntry(final Card theCard, final boolean isFoil) {
        // Build set collector code WITHOUT "Foil" suffix in name
        // Just add "f" to code for foils: "MKM 1" or "MKM 1f"
        this.mySetCollectorCode = theCard.getSetCode() + " " +
                theCard.getCollectorNumber() +
                (isFoil ? "f" : "");

        // Build card name with frame effect
        StringBuilder nameBuilder = new StringBuilder(theCard.getName());

        // Add frame effect if present
        String frameDisplay = theCard.getFrameEffectDisplay();
        if (frameDisplay != null) {
            nameBuilder.append(" - ").append(frameDisplay);
        }

        // Don't add "Foil" to the name - it's indicated by the "f" in the code
        this.myCardName = nameBuilder.toString();

        // Get the appropriate price
        this.myPrice = isFoil ? theCard.getFoilPriceAsBigDecimal() :
                theCard.getPriceAsBigDecimal();

        // Store rarity for pricing rules
        this.myRarity = theCard.getRarity();

        // Store artist
        this.myArtist = theCard.getArtist();
    }

    /**
     * Full constructor for custom entries
     */
    public CardEntry(final String theSetCollectorCode, final String theCardName,
                     final BigDecimal thePrice, final String theRarity, final String theArtist) {
        this.mySetCollectorCode = theSetCollectorCode;
        this.myCardName = theCardName;
        this.myPrice = thePrice;
        this.myRarity = theRarity;
        this.myArtist = theArtist;
    }

    // Getters
    public String getSetCollectorCode() {
        return mySetCollectorCode;
    }

    public String getCardName() {
        return myCardName;
    }

    public BigDecimal getPrice() {
        return myPrice;
    }

    /**
     * Returns price as a formatted string with dollar sign
     */
    public String getPriceAsString() {
        return String.format("$%.2f", myPrice);
    }

    /**
     * Returns rounded price according to business rules:
     *
     * Minimums by rarity:
     * - Rare/Mythic: $0.50
     * - Uncommon: $0.25
     * - Common: $0.10
     *
     * Rounding rules:
     * - Below $10: Round to nearest $0.50
     * - $10 and above: Round to nearest $1.00
     */
    public BigDecimal getRoundedPrice() {
        // Apply minimums based on rarity
        BigDecimal minimum = getMinimumByRarity();
        BigDecimal priceToRound = myPrice.max(minimum); // Use max of price and minimum

        if (priceToRound.compareTo(BigDecimal.TEN) < 0) {
            // Below $10 - round to nearest $0.50
            BigDecimal rounded = priceToRound.divide(new BigDecimal("0.5"), 0, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("0.5"));
            // Ensure we don't go below minimum after rounding
            return rounded.max(minimum);
        } else {
            // $10 and above - round to nearest $1.00
            return priceToRound.setScale(0, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Returns the minimum price based on card rarity
     */
    private BigDecimal getMinimumByRarity() {
        if (myRarity == null) {
            return new BigDecimal("0.10"); // Default to common minimum
        }

        String rarityLower = myRarity.toLowerCase();

        if (rarityLower.equals("rare") || rarityLower.equals("mythic")) {
            return new BigDecimal("0.50");
        } else if (rarityLower.equals("uncommon")) {
            return new BigDecimal("0.25");
        } else {
            // Common and everything else
            return new BigDecimal("0.10");
        }
    }

    /**
     * Checks if this entry has a valid (non-zero) price
     */
    public boolean hasValidPrice() {
        return myPrice != null && myPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Determines if this is a foil entry based on the collector code
     */
    public boolean isFoil() {
        return mySetCollectorCode.endsWith("F");
    }

    @Override
    public String toString() {
        return String.format("%s | %s | %s",
                mySetCollectorCode, myCardName, getPriceAsString());
    }

    /**
     * Converts this entry to a CSV row for Import Utility format
     * Format: DEPARTMENT,CATEGORY,CODE,DESCRIPTION,EXTENDED DESCRIPTION,SUB DESCRIPTION,TAX,PRICE
     */
    public String toImportUtilityRow() {
        BigDecimal roundedPrice = getRoundedPrice();

        // Card name (description) - quote if contains comma
        String cardName = myCardName.replace("\"", "\"\"");
        String description = cardName.contains(",") ? "\"" + cardName + "\"" : cardName;

        // Artist (extended description) - quote if contains comma
        String artist = myArtist != null ? myArtist : "";
        if (artist.contains(",")) {
            artist = "\"" + artist.replace("\"", "\"\"") + "\"";
        }

        // Rarity abbreviation (sub description)
        String rarityAbbrev = getRarityAbbreviation();

        // Format: DEPARTMENT,CATEGORY,CODE,DESCRIPTION,EXTENDED DESCRIPTION,SUB DESCRIPTION,TAX,PRICE
        return String.format("5,5.2,%s,%s,%s,%s,TAX,%.2f",
                mySetCollectorCode,
                description,
                artist,
                rarityAbbrev,
                roundedPrice);
    }

    /**
     * Converts this entry to a CSV row for Item Wizard format
     * Format: CODE,DESCRIPTION,,0,0.0,0,0,0,PRICE
     */
    public String toItemWizardRow() {
        BigDecimal roundedPrice = getRoundedPrice();
        String cardName = myCardName.replace("\"", "\"\"");

        // Only quote the description if it contains a comma
        String description = cardName.contains(",") ? "\"" + cardName + "\"" : cardName;

        // Format: CODE,DESCRIPTION,,0,0.0,0,0,0,PRICE
        return String.format("%s,%s,,0,0.0,0,0,0,%.2f",
                mySetCollectorCode,
                description,
                roundedPrice);
    }

    public String toZeroOutItems() {
        BigDecimal roundedPrice = getRoundedPrice();
        String cardName = myCardName.replace("\"", "\"\"");

        // Only quote the description if it contains a comma
        String description = cardName.contains(",") ? "\"" + cardName + "\"" : cardName;

        // Artist (extended description) - quote if contains comma
        String artist = myArtist != null ? myArtist : "";
        if (artist.contains(",")) {
            artist = "\"" + artist.replace("\"", "\"\"") + "\"";
        }

        // Format: CODE,DESCRIPTION,EXTENDED DESCRIPTION,ON_HAND-QTY,NEW ON-HAND QTY
        return String.format("%s,%s,%s,,0",
                mySetCollectorCode,
                description,
                artist);
    }

    /**
     * Gets abbreviated rarity code
     * c = common, u = uncommon, r = rare, m = mythic
     */
    private String getRarityAbbreviation() {
        if (myRarity == null) {
            return "";
        }

        String rarityLower = myRarity.toLowerCase();

        switch (rarityLower) {
            case "common": return "C";
            case "uncommon": return "U";
            case "rare": return "R";
            case "mythic": return "M";
            default: return rarityLower.toUpperCase();
        }
    }
}