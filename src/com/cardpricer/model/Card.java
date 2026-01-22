package com.cardpricer.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Card {
    private String myName;
    private String mySetCode;
    private String myCollectorNumber;
    private String myRarity;
    private String myPrice;
    private String myFoilPrice;
    private List<String> myFrameEffects;
    private String myArtist;

    // Default constructor
    public Card() {
        this.myPrice = "N/A";
        this.myFoilPrice = "N/A";
        this.myFrameEffects = new ArrayList<>();
    }

    // Constructor with essential fields
    public Card(final String theName, final String theSetCode, final String theCollectorNumber) {
        this();
        this.myName = theName;
        this.mySetCode = theSetCode;
        this.myCollectorNumber = theCollectorNumber;
    }

    // Getters and Setters
    public String getName() {
        return this.myName;
    }

    public void setName(final String theName) {
        if (theName == null || theName.trim().isEmpty()) {
            throw new IllegalArgumentException("Card name cannot be null or empty");
        }
        this.myName = theName.trim();
    }

    public String getSetCode() {
        return this.mySetCode;
    }

    public void setSetCode(final String theSetCode) {
        if (theSetCode == null || theSetCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Set code cannot be null or empty");
        }

        this.mySetCode = theSetCode.trim().toUpperCase();
    }

    public String getCollectorNumber() {
        return this.myCollectorNumber;
    }

    public void setCollectorNumber(final String theCollectorNumber) {
        if (theCollectorNumber == null || theCollectorNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Collector number cannot be null or empty");
        }
        this.myCollectorNumber = theCollectorNumber.trim();
    }

    public String getRarity() {
        return this.myRarity;
    }

    public void setRarity(final String theRarity) {
        if (theRarity == null || theRarity.trim().isEmpty()) {
            throw new IllegalArgumentException("Rarity cannot be null or empty");
        }
        this.myRarity = theRarity.toLowerCase().trim();
    }

    public String getPrice() {
        return this.myPrice;
    }

    public BigDecimal getPriceAsBigDecimal() {
        return parsePriceString(this.myPrice);
    }

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

    public String getFoilPrice() {
        return this.myFoilPrice;
    }

    public BigDecimal getFoilPriceAsBigDecimal() {
        return parsePriceString(this.myFoilPrice);
    }

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

    public List<String> getFrameEffects() {
        return new ArrayList<>(this.myFrameEffects);
    }

    public void setFrameEffects(final List<String> theFrameEffects) {
        this.myFrameEffects = theFrameEffects != null ?
                new ArrayList<>(theFrameEffects) : new ArrayList<>();
    }

    public String getArtist() {
        return this.myArtist;
    }

    public void setArtist(final String theArtist) {
        this.myArtist = theArtist != null ? theArtist.trim() : null;
    }

    // Helper methods
    public boolean hasNormalPrice() {
        return myPrice != null && !myPrice.equals("N/A");
    }

    public boolean hasFoilPrice() {
        return myFoilPrice != null && !myFoilPrice.equals("N/A");
    }

    public String getDisplayPrice(boolean isFoil) {
        return isFoil ? myFoilPrice : myPrice;
    }

    public boolean hasFrameEffects() {
        return myFrameEffects != null && !myFrameEffects.isEmpty();
    }

    public boolean isShowcase() {
        return myFrameEffects != null && myFrameEffects.contains("showcase");
    }

    public boolean isExtendedArt() {
        return myFrameEffects != null && myFrameEffects.contains("extendedart");
    }

    public boolean isBorderless() {
        return myFrameEffects != null && myFrameEffects.contains("borderless");
    }

    public boolean isEtched() {
        return myFrameEffects != null && myFrameEffects.contains("etched");
    }

    public String getPrimaryFrameEffect() {
        if (myFrameEffects == null || myFrameEffects.isEmpty()) {
            return null;
        }
        return myFrameEffects.get(0);
    }

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