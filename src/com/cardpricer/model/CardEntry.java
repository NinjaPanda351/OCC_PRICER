package com.cardpricer.model;

import com.cardpricer.service.PricingService;
import com.cardpricer.service.CardCsvEncoder;
import com.cardpricer.service.CsvExportService.ExportFormat;
import java.util.Locale;

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

    /** Returns the set-and-collector code for this entry (e.g. {@code "MKM 1"} or {@code "MKM 1f"}). */
    public String getSetCollectorCode() {
        return mySetCollectorCode;
    }

    /** Returns the display name for this entry, including any frame-effect suffix. */
    public String getCardName() {
        return myCardName;
    }

    /** Returns the raw (unrounded) price for this entry. */
    public BigDecimal getPrice() {
        return myPrice;
    }

    /**
     * Returns price as a formatted string with dollar sign
     */
    public String getPriceAsString() {
        return String.format(Locale.ROOT, "$%.2f", myPrice);
    }

    /**
     * Returns the rounded price according to business rules delegated to {@link PricingService}.
     *
     * <p>Minimums by rarity: Rare/Mythic $0.50 · Uncommon $0.25 · Common $0.10.
     * <p>Rounding: below $10 round to nearest $0.50; $10 and above round to nearest $1.00.
     */
    public BigDecimal getRoundedPrice() {
        return new PricingService().applyPricingRules(myPrice, myRarity);
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
        return mySetCollectorCode.toUpperCase(Locale.ROOT).endsWith("F");
    }

    @Override
    public String toString() {
        return String.format("%s | %s | %s",
                mySetCollectorCode, myCardName, getPriceAsString());
    }

    public String getRarity() { return myRarity; }
    public String getArtist() { return myArtist; }

    /** Compatibility methods; CSV schema and encoding live in CardCsvEncoder. */
    public String toImportUtilityRow() {
        return CardCsvEncoder.row(this, ExportFormat.IMPORT_UTILITY);
    }

    public String toItemWizardRow() {
        return CardCsvEncoder.row(this, ExportFormat.ITEM_WIZARD);
    }

    public String toZeroOutItems() {
        return CardCsvEncoder.row(this, ExportFormat.ITEM_WIZARD_CHANGE_QTY_ZERO);
    }
}
