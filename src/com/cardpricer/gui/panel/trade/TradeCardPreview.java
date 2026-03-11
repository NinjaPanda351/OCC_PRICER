package com.cardpricer.gui.panel.trade;

import com.cardpricer.gui.CardImagePopup;
import com.cardpricer.model.Card;
import com.cardpricer.model.ParsedCode;
import com.cardpricer.service.PricingService;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.service.ScryfallCatalogService;
import com.cardpricer.util.CardCodeParser;
import com.cardpricer.util.VintageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.util.function.Supplier;

/**
 * Manages preview state and card-fetch logic for the trade entry field.
 *
 * <p>Fires {@link CardReadyCallback} with a fully resolved {@link Card} whenever a
 * card is ready to be committed to the trade table. Handles:
 * <ul>
 *   <li>Debounced background preview as the user types</li>
 *   <li>Fetch-then-add on Enter (re-uses cached preview when code matches)</li>
 *   <li>"misc" manual entry dialog</li>
 *   <li>Manual price prompt when Scryfall returns no price for a finish</li>
 *   <li>Manual price prompt when card is not found at all</li>
 * </ul>
 */
public final class TradeCardPreview {

    private final ScryfallApiService api;
    private final PricingService pricing;
    private final Window parentWindow;
    private final JTextField cardCodeField;
    private final Supplier<CardImagePopup> imagePopup;
    private final CardReadyCallback onCardReady;

    private final JLabel previewLabel;

    // Preview state
    private Card   previewCard;
    private String previewFinish;
    private String previewOriginalSetCode = "";
    private String lastPreviewCode;
    private Timer  previewTimer;

    /**
     * @param api           Scryfall fetch service
     * @param pricing       pricing rules service
     * @param parentWindow  owner window for dialogs
     * @param cardCodeField the text field driving preview / add; cleared on dismiss
     * @param imagePopup    lazy supplier for the hover image popup
     * @param onCardReady   fires when a card (including misc / manually priced) is ready to add
     */
    public TradeCardPreview(ScryfallApiService api,
                            PricingService pricing,
                            Window parentWindow,
                            JTextField cardCodeField,
                            Supplier<CardImagePopup> imagePopup,
                            CardReadyCallback onCardReady) {
        this.api           = api;
        this.pricing       = pricing;
        this.parentWindow  = parentWindow;
        this.cardCodeField = cardCodeField;
        this.imagePopup    = imagePopup;
        this.onCardReady   = onCardReady;

        previewLabel = new JLabel("Enter a card code above...");
        previewLabel.setFont(previewLabel.getFont().deriveFont(Font.BOLD, 14f));
        previewLabel.setBorder(new EmptyBorder(10, 5, 5, 5));
    }

    /** Returns the label that displays the live card preview; embed it in the input panel. */
    public JLabel getPreviewLabel() { return previewLabel; }

    /**
     * Schedules a 500 ms debounced preview fetch for the current card code field content.
     * Call this from the key-release listener whenever the user is still typing.
     */
    public void schedulePreview() {
        if (previewTimer != null) previewTimer.stop();
        previewTimer = new Timer(500, e -> fetchPreview());
        previewTimer.setRepeats(false);
        previewTimer.start();
    }

    /**
     * Attempts to add the card described by the current code field content.
     *
     * <ul>
     *   <li>If the preview already shows a matching card, fires {@link CardReadyCallback}
     *       immediately without another network round-trip.</li>
     *   <li>If the code is {@code "misc"}, opens the manual entry dialog.</li>
     *   <li>Otherwise starts a background fetch and fires the callback on success.</li>
     * </ul>
     */
    public void fetchAndAdd() {
        String input = cardCodeField.getText();
        if (input == null || input.trim().isEmpty()) return;

        if (input.trim().equalsIgnoreCase("misc")) {
            promptForMiscCard();
            return;
        }

        ParsedCode parsed = CardCodeParser.parse(input);
        if (parsed == null) {
            JOptionPane.showMessageDialog(parentWindow,
                    "Invalid card code format.\nUse: SET NUMBER or SET NUMBERF\nOr type 'misc' for manual entry",
                    "Invalid Format", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Re-use cached preview if the code hasn't changed
        String code = parsed.setCode() + " " + parsed.collectorNumber();
        if (previewCard != null && lastPreviewCode != null && lastPreviewCode.equals(code)) {
            onCardReady.onCardReady(previewCard, previewFinish, previewOriginalSetCode);
            return;
        }

        clear();
        startFetchAndAdd(parsed);
    }

    /**
     * Installs a card returned by an external dialog (e.g. {@code CardSearchDialog})
     * as the current preview without fetching from the network.
     *
     * @param card            the selected card
     * @param finish          finish code (may be null or empty for normal)
     * @param originalSetCode the card's set code (used for PLST detection later)
     */
    public void setPreview(Card card, String finish, String originalSetCode) {
        lastPreviewCode        = card.getSetCode() + " " + card.getCollectorNumber();
        previewOriginalSetCode = originalSetCode != null ? originalSetCode : card.getSetCode();
        displayPreview(card, finish != null ? finish : "");
    }

    /** Resets preview state and restores the placeholder label text. */
    public void clear() {
        previewCard            = null;
        previewFinish          = null;
        lastPreviewCode        = null;
        previewOriginalSetCode = "";
        previewLabel.setText("Enter a card code above...");
        previewLabel.setForeground(UIManager.getColor("Label.foreground"));
    }

    // ── Private implementation ────────────────────────────────────────────────

    /** Background preview (debounced; does not commit the card). */
    private void fetchPreview() {
        String input = cardCodeField.getText();
        if (input == null || input.trim().isEmpty() || input.trim().length() < 3) {
            clear();
            return;
        }

        ParsedCode parsed = CardCodeParser.parse(input);
        if (parsed == null) { clear(); return; }

        String fetchingCode = parsed.setCode() + " " + parsed.collectorNumber();
        if (lastPreviewCode != null && !lastPreviewCode.equals(fetchingCode)) clear();

        new SwingWorker<Card, Void>() {
            @Override protected Card doInBackground() throws Exception {
                java.util.Optional<Card> hit = ScryfallCatalogService.getInstance()
                        .lookup(parsed.setCode(), parsed.collectorNumber());
                return hit.isPresent() ? hit.get()
                        : api.fetchCard(parsed.setCode(), parsed.collectorNumber());
            }

            @Override protected void done() {
                try {
                    Card card = get();
                    // Discard stale result if user has typed something new
                    String currentInput = cardCodeField.getText();
                    ParsedCode currentParsed = CardCodeParser.parse(currentInput);
                    if (currentParsed != null) {
                        String currentCode = currentParsed.setCode() + " " + currentParsed.collectorNumber();
                        if (currentCode.equals(fetchingCode)) {
                            lastPreviewCode        = fetchingCode;
                            previewOriginalSetCode = parsed.setCode();
                            displayPreview(card, parsed.finish());
                        }
                    }
                } catch (Exception e) {
                    clear();
                }
            }
        }.execute();
    }

    /** Fetches card and fires callback on success; handles missing-price and not-found cases. */
    private void startFetchAndAdd(ParsedCode parsed) {
        previewLabel.setText("Loading...");

        new SwingWorker<Card, Void>() {
            @Override protected Card doInBackground() throws Exception {
                java.util.Optional<Card> hit = ScryfallCatalogService.getInstance()
                        .lookup(parsed.setCode(), parsed.collectorNumber());
                return hit.isPresent() ? hit.get()
                        : api.fetchCard(parsed.setCode(), parsed.collectorNumber());
            }

            @Override protected void done() {
                try {
                    Card card = get();

                    boolean isFoil   = "F".equals(parsed.finish())
                                    || "E".equals(parsed.finish())
                                    || "S".equals(parsed.finish());
                    boolean hasPrice = isFoil ? card.hasFoilPrice() : card.hasNormalPrice();

                    if (!hasPrice) {
                        promptForManualPriceOnCard(card, parsed);
                        return;
                    }

                    previewOriginalSetCode = parsed.setCode();
                    displayPreview(card, parsed.finish());

                    // Auto-show image popup for vintage cards so the trader can
                    // visually verify the card before committing.
                    if (VintageUtil.isVintageSet(card.getSetCode()) && card.getImageUrl() != null) {
                        try {
                            Point loc = previewLabel.getLocationOnScreen();
                            imagePopup.get().show(card.getImageUrl(),
                                    new Point(loc.x + previewLabel.getWidth() + 12, loc.y));
                        } catch (java.awt.IllegalComponentStateException ignored) {}
                    }

                    onCardReady.onCardReady(card, parsed.finish(), parsed.setCode());

                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("not found")) {
                        promptForManualPrice(parsed);
                    } else {
                        JOptionPane.showMessageDialog(parentWindow,
                                "Failed to fetch card: " + e.getMessage(),
                                "API Error", JOptionPane.ERROR_MESSAGE);
                        clear();
                    }
                }
            }
        }.execute();
    }

    /**
     * Shows a price-input dialog when the card code was not found in Scryfall.
     * Builds a placeholder {@link Card} and fires the callback so the normal
     * add-card path (including high-value check) still runs.
     */
    private void promptForManualPrice(ParsedCode parsed) {
        String priceInput = JOptionPane.showInputDialog(parentWindow,
                String.format("Card %s %s not found in Scryfall.\nEnter manual price:",
                        parsed.setCode(), parsed.collectorNumber()),
                "Manual Price Entry", JOptionPane.QUESTION_MESSAGE);

        if (priceInput == null || priceInput.trim().isEmpty()) {
            dismissToCodeField();
            return;
        }

        try {
            BigDecimal price = parsePositivePrice(priceInput);
            if (price == null) { dismissToCodeField(); return; }

            Card miscCard = buildMiscCard("Misc Magic Card", parsed.setCode(),
                    parsed.collectorNumber(), price);
            onCardReady.onCardReady(miscCard, parsed.finish(), parsed.setCode());

        } catch (NumberFormatException e) {
            showPriceFormatError();
            dismissToCodeField();
        }
    }

    /**
     * Shows a price-input dialog when the card exists in Scryfall but has no listed
     * price for the requested finish. Sets the price on the card then fires the callback.
     */
    private void promptForManualPriceOnCard(Card card, ParsedCode parsed) {
        boolean isFoil = "F".equals(parsed.finish())
                      || "E".equals(parsed.finish())
                      || "S".equals(parsed.finish());
        String finishType = "E".equals(parsed.finish()) ? "etched"
                : "S".equals(parsed.finish())           ? "surge foil"
                : isFoil                                ? "foil" : "normal";

        String priceInput = JOptionPane.showInputDialog(parentWindow,
                String.format("Card '%s' has no %s price listed.\nEnter manual price:",
                        card.getName(), finishType),
                "Manual Price Entry", JOptionPane.QUESTION_MESSAGE);

        if (priceInput == null || priceInput.trim().isEmpty()) {
            dismissToCodeField();
            return;
        }

        try {
            BigDecimal price = parsePositivePrice(priceInput);
            if (price == null) { dismissToCodeField(); return; }

            if (isFoil) card.setFoilPrice(price.toString());
            else        card.setPrice(price.toString());

            previewOriginalSetCode = parsed.setCode();
            displayPreview(card, parsed.finish());
            onCardReady.onCardReady(card, parsed.finish(), parsed.setCode());

        } catch (NumberFormatException e) {
            showPriceFormatError();
            dismissToCodeField();
        }
    }

    /** Opens the manual misc-card entry dialog (name + price). */
    private void promptForMiscCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        JTextField nameField  = new JTextField(30);
        JTextField priceField = new JTextField(10);

        // Auto-focus the name field when the dialog becomes visible
        nameField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && nameField.isShowing()) {
                SwingUtilities.invokeLater(nameField::requestFocusInWindow);
            }
        });
        // Enter in name field moves to price field without submitting
        nameField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    priceField.requestFocusInWindow();
                    e.consume();
                }
            }
        });

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Card Name:"), gbc);
        gbc.gridx = 1;               panel.add(nameField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Price:"), gbc);
        gbc.gridx = 1;               panel.add(priceField, gbc);

        int result = JOptionPane.showConfirmDialog(parentWindow, panel,
                "Add Misc Card", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) { dismissToCodeField(); return; }

        String cardName   = nameField.getText().trim();
        String priceInput = priceField.getText().trim();

        if (cardName.isEmpty()) cardName = "Misc Magic Card";

        if (priceInput.isEmpty()) {
            JOptionPane.showMessageDialog(parentWindow, "Price is required",
                    "Missing Price", JOptionPane.WARNING_MESSAGE);
            dismissToCodeField();
            return;
        }

        try {
            BigDecimal price = parsePositivePrice(priceInput);
            if (price == null) { dismissToCodeField(); return; }

            Card miscCard = buildMiscCard(cardName, "MISC", "1", price);
            onCardReady.onCardReady(miscCard, "", "misc");

        } catch (NumberFormatException e) {
            showPriceFormatError();
            dismissToCodeField();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void displayPreview(Card card, String finish) {
        previewCard   = card;
        previewFinish = finish;

        StringBuilder text = new StringBuilder("✓ ").append(card.getName());
        if (card.getFrameEffectDisplay() != null)
            text.append(" - ").append(card.getFrameEffectDisplay());

        BigDecimal price;
        String finishName;
        if ("F".equals(finish)) {
            price = card.getFoilPriceAsBigDecimal();    finishName = "Foil";
        } else if ("E".equals(finish)) {
            price = card.getEtchedPriceAsBigDecimal(); finishName = "Etched";
        } else if ("S".equals(finish)) {
            price = card.getFoilPriceAsBigDecimal();    finishName = "Surge Foil";
        } else {
            price = card.getPriceAsBigDecimal();        finishName = "Normal";
        }

        BigDecimal roundedPrice = pricing.applyPricingRules(price, card.getRarity());
        text.append(String.format(" (%s) - $%.2f [%s %s]",
                finishName, roundedPrice, card.getSetCode(),
                CardCodeParser.capitalize(card.getRarity())));

        previewLabel.setText(text.toString());
        previewLabel.setForeground(price.compareTo(BigDecimal.ZERO) > 0
                ? new Color(0, 120, 0) : Color.RED);
    }

    /**
     * Parses a price string (strips {@code $} and commas). Returns {@code null} and shows a
     * warning dialog if the value is {@code <= 0}.
     */
    private BigDecimal parsePositivePrice(String raw) {
        BigDecimal price = new BigDecimal(raw.replace("$", "").replace(",", "").trim());
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(parentWindow,
                    "Price must be greater than $0.00", "Invalid Price", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return price;
    }

    private Card buildMiscCard(String name, String setCode, String collNum, BigDecimal price) {
        Card card = new Card();
        card.setName(name);
        card.setSetCode(setCode);
        card.setCollectorNumber(collNum);
        card.setRarity("common");
        card.setPrice(price.toString());
        return card;
    }

    private void showPriceFormatError() {
        JOptionPane.showMessageDialog(parentWindow,
                "Invalid price format. Please enter a valid number.",
                "Invalid Price", JOptionPane.ERROR_MESSAGE);
    }

    /** Clears the code field, resets preview, and returns focus — called on dialog cancel. */
    private void dismissToCodeField() {
        cardCodeField.setText("");
        clear();
        cardCodeField.requestFocusInWindow();
    }
}
