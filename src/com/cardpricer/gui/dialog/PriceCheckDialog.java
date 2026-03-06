package com.cardpricer.gui.dialog;

import com.cardpricer.model.Card;
import com.cardpricer.model.ParsedCode;
import com.cardpricer.service.BuyRateService;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.util.AppTheme;
import com.cardpricer.util.CardCodeParser;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;

/**
 * Non-modal singleton dialog for instant price and buy-offer lookup.
 * Triggered by Ctrl+Space from TradePanel; floats alongside the trade flow.
 *
 * <p>Input auto-detects card codes ({@code TDM 3}) and card names (fuzzy search).
 * Finish radio buttons are automatically disabled when prices are unavailable.
 * The "Add to Trade" button invokes the provided callback and resets the dialog.
 */
public class PriceCheckDialog extends JDialog {

    private static PriceCheckDialog INSTANCE;

    private final ScryfallApiService apiService;
    private final BuyRateService buyRateService;

    private Card currentCard;
    private BiConsumer<Card, String> onAddCallback;
    private Timer debounceTimer;
    private SwingWorker<?, ?> activeWorker;

    // UI components
    private JTextField inputField;
    private JLabel nameLabel;
    private JLabel setInfoLabel;
    private JLabel normalPriceLabel;
    private JLabel foilPriceLabel;
    private JLabel etchedPriceLabel;
    private JLabel creditOfferLabel;
    private JLabel checkOfferLabel;
    private JRadioButton normalRadio;
    private JRadioButton foilRadio;
    private JRadioButton etchedRadio;
    private JButton addButton;
    private JLabel statusLabel;

    private PriceCheckDialog(Window owner, BiConsumer<Card, String> onAddToTrade) {
        super(owner, "Quick Price Check", ModalityType.MODELESS);
        this.onAddCallback = onAddToTrade;
        this.apiService = new ScryfallApiService();
        this.buyRateService = new BuyRateService();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);
        buildUI();
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                INSTANCE = null;
            }
        });
    }

    /**
     * Opens (or brings to front) the price check dialog.
     * If already visible, clears input, updates the callback, and requests focus.
     *
     * @param owner        parent window for positioning
     * @param onAddToTrade callback invoked when "Add to Trade" is clicked;
     *                     receives the {@link Card} and the finish code
     *                     ({@code ""} normal, {@code "F"} foil, {@code "E"} etched)
     */
    public static void show(Window owner, BiConsumer<Card, String> onAddToTrade) {
        if (INSTANCE != null && INSTANCE.isVisible()) {
            INSTANCE.onAddCallback = onAddToTrade;
            INSTANCE.inputField.setText("");
            INSTANCE.clearDisplay();
            INSTANCE.toFront();
            INSTANCE.requestFocus();
            INSTANCE.inputField.requestFocusInWindow();
            return;
        }
        INSTANCE = new PriceCheckDialog(owner, onAddToTrade);
        INSTANCE.setVisible(true);
        INSTANCE.inputField.requestFocusInWindow();
    }

    // -----------------------------------------------------------------------
    // UI construction
    // -----------------------------------------------------------------------

    private void buildUI() {
        JPanel content = new JPanel(new BorderLayout(10, 8));
        content.setBorder(new EmptyBorder(14, 14, 14, 14));

        // ── Input row ───────────────────────────────────────────────────────
        JPanel inputWrapper = new JPanel(new BorderLayout(4, 4));
        inputField = new JTextField(28);
        inputField.setFont(inputField.getFont().deriveFont(Font.PLAIN, 15f));
        inputField.setToolTipText("Enter set code (TDM 3) or card name");
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                } else {
                    scheduleSearch();
                }
            }
        });
        JLabel hintLabel = new JLabel("Enter set code (TDM 3) or card name");
        hintLabel.setFont(hintLabel.getFont().deriveFont(11f));
        hintLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        inputWrapper.add(inputField, BorderLayout.CENTER);
        inputWrapper.add(hintLabel, BorderLayout.SOUTH);
        content.add(inputWrapper, BorderLayout.NORTH);

        // ── Card info panel ─────────────────────────────────────────────────
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(AppTheme.sectionBorder("Card Info"));

        nameLabel        = new JLabel("\u2014");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13f));
        setInfoLabel     = new JLabel("\u2014");
        normalPriceLabel = new JLabel("\u2014");
        foilPriceLabel   = new JLabel("\u2014");
        etchedPriceLabel = new JLabel("\u2014");
        creditOfferLabel = new JLabel("\u2014");
        checkOfferLabel  = new JLabel("\u2014");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(3, 5, 3, 5);
        gbc.anchor  = GridBagConstraints.WEST;

        int row = 0;
        addInfoRow(infoPanel, gbc, row++, "Name",         nameLabel);
        addInfoRow(infoPanel, gbc, row++, "Set",          setInfoLabel);
        addInfoRow(infoPanel, gbc, row++, "Normal",       normalPriceLabel);
        addInfoRow(infoPanel, gbc, row++, "Foil",         foilPriceLabel);
        addInfoRow(infoPanel, gbc, row++, "Etched",       etchedPriceLabel);

        // Separator
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        gbc.fill  = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        infoPanel.add(new JSeparator(), gbc);
        gbc.fill  = GridBagConstraints.NONE; gbc.gridwidth = 1; gbc.weightx = 0;

        addInfoRow(infoPanel, gbc, row++, "Credit Offer", creditOfferLabel);
        addInfoRow(infoPanel, gbc, row,   "Check Offer",  checkOfferLabel);

        content.add(infoPanel, BorderLayout.CENTER);

        // ── Bottom: finish radios + status + add button ─────────────────────
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 6));

        JPanel finishPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        finishPanel.add(new JLabel("Finish:"));
        ButtonGroup group = new ButtonGroup();
        normalRadio = new JRadioButton("Normal");
        foilRadio   = new JRadioButton("Foil");
        etchedRadio = new JRadioButton("Etched");
        normalRadio.setSelected(true);
        group.add(normalRadio);
        group.add(foilRadio);
        group.add(etchedRadio);
        finishPanel.add(normalRadio);
        finishPanel.add(foilRadio);
        finishPanel.add(etchedRadio);

        java.awt.event.ActionListener radioListener = e -> updateOfferDisplay();
        normalRadio.addActionListener(radioListener);
        foilRadio  .addActionListener(radioListener);
        etchedRadio.addActionListener(radioListener);

        JPanel actionRow = new JPanel(new BorderLayout(8, 0));
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(AppTheme.SUCCESS);
        addButton = AppTheme.primaryButton("Add to Trade");
        addButton.setEnabled(false);
        addButton.addActionListener(e -> doAddToTrade());
        actionRow.add(statusLabel, BorderLayout.CENTER);
        actionRow.add(addButton,   BorderLayout.EAST);

        bottomPanel.add(finishPanel, BorderLayout.NORTH);
        bottomPanel.add(actionRow,   BorderLayout.SOUTH);

        content.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void addInfoRow(JPanel panel, GridBagConstraints gbc,
                            int row, String labelText, JLabel valueLabel) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel(labelText + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(valueLabel, gbc);
    }

    // -----------------------------------------------------------------------
    // Search logic
    // -----------------------------------------------------------------------

    private void scheduleSearch() {
        if (debounceTimer != null) debounceTimer.stop();
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            if (activeWorker != null) activeWorker.cancel(true);
            clearDisplay();
            return;
        }
        debounceTimer = new Timer(500, e -> doSearch(text));
        debounceTimer.setRepeats(false);
        debounceTimer.start();
    }

    private void doSearch(String input) {
        if (activeWorker != null && !activeWorker.isDone()) {
            activeWorker.cancel(true);
        }
        statusLabel.setText("Searching\u2026");
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        addButton.setEnabled(false);

        final ParsedCode parsed = CardCodeParser.parse(input);

        activeWorker = new SwingWorker<Card, Void>() {
            @Override
            protected Card doInBackground() throws Exception {
                if (parsed != null) {
                    return apiService.fetchCard(parsed.setCode, parsed.collectorNumber);
                } else {
                    String encoded = java.net.URLEncoder.encode(input, "UTF-8");
                    String url = "https://api.scryfall.com/cards/named?fuzzy=" + encoded;
                    JSONObject json = apiService.makeApiCall(url);
                    return apiService.parseCardFromJson(json);
                }
            }

            @Override
            protected void done() {
                try {
                    Card card = get();
                    currentCard = card;
                    String initialFinish = (parsed != null) ? parsed.finish : "";
                    onCardFetched(card, initialFinish);
                } catch (CancellationException ignored) {
                    // Worker was cancelled — keep UI as-is
                } catch (Exception e) {
                    statusLabel.setText("Not found");
                    statusLabel.setForeground(AppTheme.DANGER);
                    clearDisplay();
                }
            }
        };
        activeWorker.execute();
    }

    // -----------------------------------------------------------------------
    // UI update helpers
    // -----------------------------------------------------------------------

    private void onCardFetched(Card card, String initialFinish) {
        nameLabel.setText(card.getName());

        String rarity = card.getRarity() != null
                ? card.getRarity().substring(0, 1).toUpperCase() + card.getRarity().substring(1)
                : "";
        setInfoLabel.setText(card.getSetCode() + "  #" + card.getCollectorNumber()
                + (rarity.isEmpty() ? "" : "  \u2022  " + rarity));

        normalPriceLabel.setText(card.hasNormalPrice() ? "$" + card.getPrice()       : "N/A");
        foilPriceLabel  .setText(card.hasFoilPrice()   ? "$" + card.getFoilPrice()   : "N/A");
        etchedPriceLabel.setText(card.hasEtchedPrice() ? "$" + card.getEtchedPrice() : "N/A");

        // Enable/disable radios based on available prices
        normalRadio.setEnabled(card.hasNormalPrice());
        foilRadio  .setEnabled(card.hasFoilPrice());
        etchedRadio.setEnabled(card.hasEtchedPrice());

        // Select radio matching the initial finish from the parsed code
        if ("E".equals(initialFinish) && card.hasEtchedPrice()) {
            etchedRadio.setSelected(true);
        } else if (("F".equals(initialFinish) || "S".equals(initialFinish)) && card.hasFoilPrice()) {
            foilRadio.setSelected(true);
        } else if (card.hasNormalPrice()) {
            normalRadio.setSelected(true);
        } else if (card.hasFoilPrice()) {
            foilRadio.setSelected(true);
        } else if (card.hasEtchedPrice()) {
            etchedRadio.setSelected(true);
        }

        addButton.setEnabled(true);
        statusLabel.setText(" ");
        statusLabel.setForeground(AppTheme.SUCCESS);
        updateOfferDisplay();
    }

    private void updateOfferDisplay() {
        if (currentCard == null) return;

        BigDecimal selectedPrice;
        if (etchedRadio.isSelected() && currentCard.hasEtchedPrice()) {
            selectedPrice = currentCard.getEtchedPriceAsBigDecimal();
        } else if (foilRadio.isSelected() && currentCard.hasFoilPrice()) {
            selectedPrice = currentCard.getFoilPriceAsBigDecimal();
        } else if (currentCard.hasNormalPrice()) {
            selectedPrice = currentCard.getPriceAsBigDecimal();
        } else {
            creditOfferLabel.setText("N/A");
            checkOfferLabel .setText("N/A");
            return;
        }

        if (selectedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            creditOfferLabel.setText("N/A");
            checkOfferLabel .setText("N/A");
            return;
        }

        BuyRateService.PayoutResult payout = buyRateService.computePayout(
                currentCard.getSetCode(),
                currentCard.getCollectorNumber(),
                currentCard.getName(),
                selectedPrice);

        int creditPct = payout.appliedCreditRate().multiply(BigDecimal.valueOf(100)).intValue();
        int checkPct  = payout.appliedCheckRate() .multiply(BigDecimal.valueOf(100)).intValue();

        creditOfferLabel.setText(String.format("$%.2f  (%d%%)", payout.creditPayout(), creditPct));
        checkOfferLabel .setText(String.format("$%.2f  (%d%%)", payout.checkPayout(),  checkPct));
    }

    private void doAddToTrade() {
        if (currentCard == null || onAddCallback == null) return;

        String finish;
        if (etchedRadio.isSelected()) {
            finish = "E";
        } else if (foilRadio.isSelected()) {
            finish = "F";
        } else {
            finish = "";
        }

        onAddCallback.accept(currentCard, finish);

        statusLabel.setText("\u2713 Added");
        statusLabel.setForeground(AppTheme.SUCCESS);

        // Clear the status message after 2 seconds
        Timer clearTimer = new Timer(2000, e -> {
            if (statusLabel.getText().startsWith("\u2713")) {
                statusLabel.setText(" ");
            }
        });
        clearTimer.setRepeats(false);
        clearTimer.start();

        inputField.setText("");
        clearDisplay();
        inputField.requestFocusInWindow();
    }

    private void clearDisplay() {
        currentCard = null;
        nameLabel       .setText("\u2014");
        setInfoLabel    .setText("\u2014");
        normalPriceLabel.setText("\u2014");
        foilPriceLabel  .setText("\u2014");
        etchedPriceLabel.setText("\u2014");
        creditOfferLabel.setText("\u2014");
        checkOfferLabel .setText("\u2014");
        normalRadio.setEnabled(true);
        foilRadio  .setEnabled(true);
        etchedRadio.setEnabled(true);
        normalRadio.setSelected(true);
        addButton.setEnabled(false);
    }
}
