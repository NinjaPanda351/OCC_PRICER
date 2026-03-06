package com.cardpricer.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Magic: The Gathering card with pricing, rarity, and frame-effect data
 * as returned by the Scryfall API.
 */
public class Card {
    private String myName;
    private String mySetCode;
    private String myCollectorNumber;
    private String myRarity;
    private String myPrice;
    private String myFoilPrice;
    private String myEtchedPrice;
    private List<String> myFrameEffects;
    private String myArtist;
    private String myImageUrl;
    private boolean reserved;

    /** Constructs a Card with all price fields defaulting to {@code "N/A"}. */
    public Card() {
        this.myPrice = "N/A";
        this.myFoilPrice = "N/A";
        this.myEtchedPrice = "N/A";
        this.myFrameEffects = new ArrayList<>();
    }

    /**
     * Constructs a Card with the three identifying fields.
     *
     * @param theName            card name
     * @param theSetCode         set code (will be stored uppercase)
     * @param theCollectorNumber collector number string
     */
    public Card(final String theName, final String theSetCode, final String theCollectorNumber) {
        this();
        this.myName = theName;
        this.mySetCode = theSetCode;
        this.myCollectorNumber = theCollectorNumber;
    }

    // Getters and Setters
    /** Returns the card name. */
    public String getName() {
        return this.myName;
    }

    /**
     * Sets the card name.
     *
     * @param theName card name; must not be {@code null} or blank
     * @throws IllegalArgumentException if {@code theName} is null or empty
     */
    public void setName(final String theName) {
        if (theName == null || theName.trim().isEmpty()) {
            throw new IllegalArgumentException("Card name cannot be null or empty");
        }
        this.myName = theName.trim();
    }

    /** Returns the set code (uppercase). */
    public String getSetCode() {
        return this.mySetCode;
    }

    /**
     * Sets the set code, trimming whitespace and converting to uppercase.
     *
     * @param theSetCode set code; must not be {@code null} or blank
     * @throws IllegalArgumentException if {@code theSetCode} is null or empty
     */
    public void setSetCode(final String theSetCode) {
        if (theSetCode == null || theSetCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Set code cannot be null or empty");
        }

        this.mySetCode = theSetCode.trim().toUpperCase();
    }

    /** Returns the collector number string. */
    public String getCollectorNumber() {
        return this.myCollectorNumber;
    }

    /**
     * Sets the collector number.
     *
     * @param theCollectorNumber collector number; must not be {@code null} or blank
     * @throws IllegalArgumentException if {@code theCollectorNumber} is null or empty
     */
    public void setCollectorNumber(final String theCollectorNumber) {
        if (theCollectorNumber == null || theCollectorNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Collector number cannot be null or empty");
        }
        this.myCollectorNumber = theCollectorNumber.trim();
    }

    /** Returns the rarity string (lowercase), e.g. {@code "rare"} or {@code "mythic"}. */
    public String getRarity() {
        return this.myRarity;
    }

    /**
     * Sets the rarity, trimming whitespace and converting to lowercase.
     *
     * @param theRarity rarity string; must not be {@code null} or blank
     * @throws IllegalArgumentException if {@code theRarity} is null or empty
     */
    public void setRarity(final String theRarity) {
        if (theRarity == null || theRarity.trim().isEmpty()) {
            throw new IllegalArgumentException("Rarity cannot be null or empty");
        }
        this.myRarity = theRarity.toLowerCase().trim();
    }

    /** Returns the normal (non-foil) price string, or {@code "N/A"} if unavailable. */
    public String getPrice() {
        return this.myPrice;
    }

    /**
     * Returns the normal price as a {@link BigDecimal}, or {@link BigDecimal#ZERO} if
     * the price is {@code "N/A"} or absent.
     */
    public BigDecimal getPriceAsBigDecimal() {
        return parsePriceString(this.myPrice);
    }

    /**
     * Sets the normal price.
     *
     * @param thePrice decimal price string (e.g. {@code "1.23"}), {@code "N/A"}, or {@code null}
     * @throws IllegalArgumentException if the string is neither {@code "N/A"} nor a valid decimal
     */
    public void setPrice(final String thePrice) {
        if (thePrice == null) {
            this.myPrice = "N/A";
            return;
        }
        if (!thePrice.equals("N/A")) {
            try {
                new BigDecimal(thePrice);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid price format: " + thePrice);
            }
        }
        this.myPrice = thePrice;
    }

    /** Returns the foil price string, or {@code "N/A"} if unavailable. */
    public String getFoilPrice() {
        return this.myFoilPrice;
    }

    /**
     * Returns the foil price as a {@link BigDecimal}, or {@link BigDecimal#ZERO} if
     * the price is {@code "N/A"} or absent.
     */
    public BigDecimal getFoilPriceAsBigDecimal() {
        return parsePriceString(this.myFoilPrice);
    }

    /**
     * Sets the foil price.
     *
     * @param theFoilPrice decimal price string, {@code "N/A"}, or {@code null}
     * @throws IllegalArgumentException if the string is not a valid decimal and not {@code "N/A"}
     */
    public void setFoilPrice(final String theFoilPrice) {
        if (theFoilPrice == null) {
            this.myFoilPrice = "N/A";
            return;
        }
        if (!theFoilPrice.equals("N/A")) {
            try {
                new BigDecimal(theFoilPrice);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid foil price format: " + theFoilPrice);
            }
        }
        this.myFoilPrice = theFoilPrice;
    }

    /** Returns the etched-foil price string, or {@code "N/A"} if unavailable. */
    public String getEtchedPrice() {
        return this.myEtchedPrice;
    }

    /**
     * Returns the etched price as a {@link BigDecimal}, or {@link BigDecimal#ZERO} if
     * the price is {@code "N/A"} or absent.
     */
    public BigDecimal getEtchedPriceAsBigDecimal() {
        return parsePriceString(this.myEtchedPrice);
    }

    /**
     * Sets the etched-foil price.
     *
     * @param theEtchedPrice decimal price string, {@code "N/A"}, or {@code null}
     * @throws IllegalArgumentException if the string is not a valid decimal and not {@code "N/A"}
     */
    public void setEtchedPrice(final String theEtchedPrice) {
        if (theEtchedPrice == null) {
            this.myEtchedPrice = "N/A";
            return;
        }
        if (!theEtchedPrice.equals("N/A")) {
            try {
                new BigDecimal(theEtchedPrice);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid etched price format: " + theEtchedPrice);
            }
        }
        this.myEtchedPrice = theEtchedPrice;
    }

    /** Returns {@code true} if an etched-foil price is available (not {@code "N/A"}). */
    public boolean hasEtchedPrice() {
        return !this.myEtchedPrice.equals("N/A");
    }

    /** Returns a defensive copy of the list of frame-effect identifiers for this card. */
    public List<String> getFrameEffects() {
        return new ArrayList<>(this.myFrameEffects);
    }

    /**
     * Sets the frame-effect list.
     *
     * @param theFrameEffects list of Scryfall frame-effect strings; may be {@code null}
     */
    public void setFrameEffects(final List<String> theFrameEffects) {
        this.myFrameEffects = theFrameEffects != null ?
                new ArrayList<>(theFrameEffects) : new ArrayList<>();
    }

    /** Returns the artist name for this card printing, or {@code null} if not set. */
    public String getArtist() {
        return this.myArtist;
    }

    /**
     * Sets the artist name, trimming whitespace.
     *
     * @param theArtist artist name, or {@code null}
     */
    public void setArtist(final String theArtist) {
        this.myArtist = theArtist != null ? theArtist.trim() : null;
    }

    /** Returns the Scryfall "normal" image URL for this card, or {@code null} if not fetched. */
    public String getImageUrl() {
        return myImageUrl;
    }

    /**
     * Sets the Scryfall image URL for this card.
     *
     * @param url absolute URL string, or {@code null}
     */
    public void setImageUrl(String url) {
        myImageUrl = url;
    }

    /** Returns {@code true} if this card is on the Magic: The Gathering Reserved List. */
    public boolean isReserved() {
        return reserved;
    }

    /**
     * Sets whether this card is on the Reserved List.
     *
     * @param reserved {@code true} if on the Reserved List
     */
    public void setReserved(boolean reserved) {
        this.reserved = reserved;
    }

    // Helper methods
    /** Returns {@code true} if a non-foil price is available (not {@code null} and not {@code "N/A"}). */
    public boolean hasNormalPrice() {
        return myPrice != null && !myPrice.equals("N/A");
    }

    /** Returns {@code true} if a foil price is available (not {@code null} and not {@code "N/A"}). */
    public boolean hasFoilPrice() {
        return myFoilPrice != null && !myFoilPrice.equals("N/A");
    }

    /**
     * Returns the appropriate price string based on the finish type.
     *
     * @param isFoil {@code true} to return the foil price; {@code false} for the normal price
     * @return price string, or {@code "N/A"} if unavailable
     */
    public String getDisplayPrice(boolean isFoil) {
        return isFoil ? myFoilPrice : myPrice;
    }

    /** Returns {@code true} if this card has at least one frame effect. */
    public boolean hasFrameEffects() {
        return myFrameEffects != null && !myFrameEffects.isEmpty();
    }

    /** Returns {@code true} if this card has the "showcase" frame effect. */
    public boolean isShowcase() {
        return myFrameEffects != null && myFrameEffects.contains("showcase");
    }

    /** Returns {@code true} if this card has the "extendedart" frame effect. */
    public boolean isExtendedArt() {
        return myFrameEffects != null && myFrameEffects.contains("extendedart");
    }

    /** Returns {@code true} if this card has the "borderless" frame effect. */
    public boolean isBorderless() {
        return myFrameEffects != null && myFrameEffects.contains("borderless");
    }

    /** Returns {@code true} if this card has the "etched" frame effect. */
    public boolean isEtched() {
        return myFrameEffects != null && myFrameEffects.contains("etched");
    }

    /**
     * Returns the first (primary) frame effect string, or {@code null} if none.
     */
    public String getPrimaryFrameEffect() {
        if (myFrameEffects == null || myFrameEffects.isEmpty()) {
            return null;
        }
        return myFrameEffects.get(0);
    }

    /**
     * Returns a single-character code for the primary frame effect
     * ({@code "S"} showcase, {@code "E"} extended art, {@code "B"} borderless,
     * {@code "T"} etched), or {@code ""} if no frame effect is present.
     */
    public String getFrameEffectCode() {
        if (!hasFrameEffects()) {
            return "";
        }

        String effect = getPrimaryFrameEffect();
        if (effect == null) {
            return "";
        }

        switch (effect.toLowerCase()) {
            case "showcase": return "S";
            case "extendedart": return "E";
            case "borderless": return "B";
            case "etched": return "T";
            default: return "";
        }
    }

    /**
     * Returns a human-readable display name for the primary frame effect
     * (e.g. {@code "Showcase"}, {@code "Extended Art"}), or {@code null} if none.
     */
    public String getFrameEffectDisplay() {
        if (!hasFrameEffects()) {
            return null;
        }

        String effect = getPrimaryFrameEffect();
        if (effect == null) {
            return null;
        }

        switch (effect.toLowerCase()) {
            case "showcase": return "Showcase";
            case "extendedart": return "Extended Art";
            case "borderless": return "Borderless";
            case "etched": return "Etched";
            case "inverted": return "Inverted";
            default: return effect;
        }
    }

    /**
     * Returns the single-character rarity abbreviation used in the POS export
     * ({@code "c"} common, {@code "u"} uncommon, {@code "r"} rare, {@code "m"} mythic),
     * or the lowercase rarity string for other values.
     */
    public String getRarityAbbreviation() {
        if (myRarity == null) {
            return "";
        }

        String rarityLower = myRarity.toLowerCase();

        switch (rarityLower) {
            case "common": return "c";
            case "uncommon": return "u";
            case "rare": return "r";
            case "mythic": return "m";
            default: return rarityLower;
        }
    }

    @Override
    public String toString() {
        return String.format("%s (%s %s) - Normal: %s, Foil: %s",
                myName, mySetCode, myCollectorNumber, myPrice, myFoilPrice);
    }

    private BigDecimal parsePriceString(String priceString) {
        if (priceString == null || priceString.equals("N/A")) {
            return BigDecimal.ZERO;
        }
        try {
            // Scryfall returns plain numbers like "0.28", not currency format
            return new BigDecimal(priceString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid price: " + priceString, e);
        }
    }
}