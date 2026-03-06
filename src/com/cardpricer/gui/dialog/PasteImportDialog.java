package com.cardpricer.gui.dialog;

import com.cardpricer.model.Card;
import com.cardpricer.model.ParsedCode;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.service.ScryfallCatalogService;
import com.cardpricer.util.AppTheme;
import com.cardpricer.util.CardCodeParser;
import com.cardpricer.util.CardConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/**
 * Modal dialog for pasting a list of card codes and bulk-importing them into
 * the trade table.
 *
 * <p>Phase 1 — input: user pastes codes; a live preview list shows ✓ valid /
 * ✗ invalid lines with zero API calls.  The "Import N Cards" button is enabled
 * only when at least one valid code is present.
 *
 * <p>Phase 2 — lookup: a {@link SwingWorker} resolves each card.  If the
 * local Scryfall catalog ({@link ScryfallCatalogService}) is loaded, cards are
 * looked up instantly; any misses fall back to individual Scryfall API calls
 * with the normal rate-limit delay.  Results stream in progressively.  When
 * complete, the provided {@code onImportComplete} callback is invoked on the EDT.
 * A "Close" button then dismisses the dialog.
 */
public class PasteImportDialog extends JDialog {

    /**
     * Result of fetching a single line from the paste list.
     *
     * @param parsed   the successfully parsed code
     * @param card     fetched card, or {@code null} on failure
     * @param errorMsg error description when {@code card} is {@code null}
     */
    public record FetchedResult(ParsedCode parsed, Card card, String errorMsg) {
        /** Returns {@code true} if the fetch succeeded. */
        public boolean ok() { return card != null; }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static final class ListItem {
        final String text;
        final boolean ok;
        ListItem(String text, boolean ok) { this.text = text; this.ok = ok; }
    }

    private static final class ItemRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof ListItem item) {
                label.setText(item.text);
                if (!isSelected) {
                    label.setForeground(item.ok ? AppTheme.SUCCESS : AppTheme.DANGER);
                }
            }
            return label;
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────────

    private final ScryfallApiService apiService;
    private final Consumer<List<FetchedResult>> onImportComplete;

    /** Valid parsed codes collected from the textarea — used by the fetch worker. */
    private final List<ParsedCode> validCodes = new ArrayList<>();

    // Phase 1 UI
    private JTextArea inputArea;
    private JLabel previewSummaryLabel;
    private DefaultListModel<ListItem> previewModel;
    private JButton importButton;

    // Phase 2 UI
    private JLabel progressLabel;
    private JProgressBar progressBar;
    private DefaultListModel<ListItem> resultModel;
    private JButton closeButton;

    // Card layout switching
    private CardLayout cardLayout;
    private JPanel cardPanel;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Creates the dialog.  Call {@link #setVisible(boolean) setVisible(true)} to show it.
     *
     * @param owner            parent window
     * @param apiService       Scryfall service for card fetching
     * @param onImportComplete callback invoked on the EDT when all fetches complete;
     *                         receives the full list of {@link FetchedResult} objects
     */
    public PasteImportDialog(Window owner, ScryfallApiService apiService,
                              Consumer<List<FetchedResult>> onImportComplete) {
        super(owner, "Paste Card List", ModalityType.APPLICATION_MODAL);
        this.apiService = apiService;
        this.onImportComplete = onImportComplete;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUI();
        setSize(480, 520);
        setLocationRelativeTo(owner);
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private void buildUI() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(new EmptyBorder(14, 14, 14, 14));

        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);
        cardPanel.add(buildPhase1Panel(), "phase1");
        cardPanel.add(buildPhase2Panel(), "phase2");

        content.add(cardPanel, BorderLayout.CENTER);
        setContentPane(content);
    }

    private JPanel buildPhase1Panel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));

        // ── Input area ───────────────────────────────────────────────────────
        JPanel inputSection = new JPanel(new BorderLayout(0, 4));
        JLabel instructionLabel = new JLabel(
                "Paste one code per line (TDM 3, DMR 15f, PLST ARB 1, \u2026):");
        instructionLabel.setFont(instructionLabel.getFont().deriveFont(Font.BOLD, 13f));
        inputSection.add(instructionLabel, BorderLayout.NORTH);

        inputArea = new JTextArea(8, 36);
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        inputArea.setLineWrap(false);
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setPreferredSize(new Dimension(450, 160));
        inputSection.add(inputScroll, BorderLayout.CENTER);
        panel.add(inputSection, BorderLayout.NORTH);

        // ── Preview section ──────────────────────────────────────────────────
        JPanel previewSection = new JPanel(new BorderLayout(0, 4));
        previewSection.setBorder(AppTheme.sectionBorder("Preview"));

        previewSummaryLabel = new JLabel("Enter codes above to preview");
        previewSummaryLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        previewSection.add(previewSummaryLabel, BorderLayout.NORTH);

        previewModel = new DefaultListModel<>();
        JList<ListItem> previewList = new JList<>(previewModel);
        previewList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        previewList.setCellRenderer(new ItemRenderer());
        previewList.setVisibleRowCount(7);
        previewSection.add(new JScrollPane(previewList), BorderLayout.CENTER);
        panel.add(previewSection, BorderLayout.CENTER);

        // ── Buttons ──────────────────────────────────────────────────────────
        importButton = AppTheme.primaryButton("Import 0 Cards");
        importButton.setEnabled(false);
        importButton.addActionListener(e -> startFetching());

        JButton cancelButton = AppTheme.secondaryButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonRow.add(importButton);
        buttonRow.add(cancelButton);
        panel.add(buttonRow, BorderLayout.SOUTH);

        // ── DocumentListener for live preview ────────────────────────────────
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { updatePreview(); }
            @Override public void removeUpdate(DocumentEvent e)  { updatePreview(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePreview(); }
        });

        return panel;
    }

    private JPanel buildPhase2Panel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));

        // ── Progress area ────────────────────────────────────────────────────
        JPanel progressSection = new JPanel(new BorderLayout(0, 4));
        progressLabel = new JLabel("Preparing\u2026");
        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.BOLD, 13f));
        progressBar   = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressSection.add(progressLabel, BorderLayout.NORTH);
        progressSection.add(progressBar,   BorderLayout.CENTER);
        panel.add(progressSection, BorderLayout.NORTH);

        // ── Results list ─────────────────────────────────────────────────────
        resultModel = new DefaultListModel<>();
        JList<ListItem> resultList = new JList<>(resultModel);
        resultList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultList.setCellRenderer(new ItemRenderer());
        JScrollPane resultScroll = new JScrollPane(resultList);
        resultScroll.setBorder(AppTheme.sectionBorder("Results"));
        panel.add(resultScroll, BorderLayout.CENTER);

        // ── Close button ─────────────────────────────────────────────────────
        closeButton = AppTheme.primaryButton("Close");
        closeButton.setEnabled(false);
        closeButton.addActionListener(e -> dispose());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonRow.add(closeButton);
        panel.add(buttonRow, BorderLayout.SOUTH);

        return panel;
    }

    // ── Preview logic ────────────────────────────────────────────────────────

    private void updatePreview() {
        previewModel.clear();
        validCodes.clear();

        String text = inputArea.getText();
        if (text.isBlank()) {
            previewSummaryLabel.setText("Enter codes above to preview");
            importButton.setText("Import 0 Cards");
            importButton.setEnabled(false);
            return;
        }

        String[] lines = text.split("\n");
        int valid = 0, invalid = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            ParsedCode parsed = CardCodeParser.parse(trimmed);
            if (parsed != null) {
                String finishDisplay = switch (parsed.finish) {
                    case "F" -> "foil";
                    case "E" -> "etched";
                    case "S" -> "surge foil";
                    default  -> "normal";
                };
                previewModel.addElement(new ListItem(
                        "\u2713 " + trimmed + "   \u2192  " + finishDisplay, true));
                validCodes.add(parsed);
                valid++;
            } else {
                previewModel.addElement(new ListItem(
                        "\u2717 \"" + trimmed + "\"   cannot parse", false));
                invalid++;
            }
        }

        if (valid + invalid == 0) {
            previewSummaryLabel.setText("Enter codes above to preview");
        } else {
            previewSummaryLabel.setText("Preview \u2014 " + valid + " valid, " + invalid + " invalid:");
        }
        importButton.setText("Import " + valid + " Card" + (valid == 1 ? "" : "s"));
        importButton.setEnabled(valid > 0);
    }

    // ── Fetch logic ──────────────────────────────────────────────────────────

    private void startFetching() {
        if (validCodes.isEmpty()) return;

        // Snapshot the valid codes so the worker owns them
        List<ParsedCode> codesToFetch = new ArrayList<>(validCodes);

        // Switch to phase 2
        cardLayout.show(cardPanel, "phase2");
        ScryfallCatalogService catalog = ScryfallCatalogService.getInstance();
        if (catalog.isLoaded()) {
            progressLabel.setText("Looking up " + codesToFetch.size()
                    + " card" + (codesToFetch.size() == 1 ? "" : "s") + " in catalog\u2026");
        } else {
            progressLabel.setText("Fetching 0 / " + codesToFetch.size() + "\u2026");
        }
        progressBar.setValue(0);

        new SwingWorker<List<FetchedResult>, FetchedResult>() {

            @Override
            protected List<FetchedResult> doInBackground() throws Exception {
                List<FetchedResult> allResults = new ArrayList<>();
                boolean lastWasApiCall = false;

                for (ParsedCode parsed : codesToFetch) {
                    if (isCancelled()) break;

                    // Throttle only between consecutive API calls
                    if (lastWasApiCall) Thread.sleep(CardConstants.API_RATE_LIMIT_MS);

                    // Try the local catalog first (O(1), instant)
                    java.util.Optional<Card> hit =
                            catalog.lookup(parsed.setCode, parsed.collectorNumber);
                    if (hit.isPresent()) {
                        FetchedResult r = new FetchedResult(parsed, hit.get(), null);
                        allResults.add(r);
                        publish(r);
                        lastWasApiCall = false;
                    } else {
                        // Catalog miss — fall back to Scryfall API
                        try {
                            Card card = apiService.fetchCard(
                                    parsed.setCode, parsed.collectorNumber);
                            FetchedResult r = new FetchedResult(parsed, card, null);
                            allResults.add(r);
                            publish(r);
                        } catch (Exception e) {
                            FetchedResult r = new FetchedResult(parsed, null, e.getMessage());
                            allResults.add(r);
                            publish(r);
                        }
                        lastWasApiCall = true;
                    }
                }
                return allResults;
            }

            @Override
            protected void process(List<FetchedResult> chunks) {
                for (FetchedResult result : chunks) {
                    String label;
                    if (result.ok()) {
                        BigDecimal price = getDisplayPrice(result);
                        String finishTag = finishTag(result.parsed().finish);
                        label = "\u2713 " + result.card().getName()
                                + finishTag
                                + "   $" + String.format("%.2f", price);
                        resultModel.addElement(new ListItem(label, true));
                    } else {
                        String code = result.parsed().setCode
                                + " " + result.parsed().collectorNumber;
                        label = "\u2717 " + code + " \u2014 "
                                + (result.errorMsg() != null ? result.errorMsg() : "fetch failed");
                        resultModel.addElement(new ListItem(label, false));
                    }

                    int done  = resultModel.getSize();
                    int total = codesToFetch.size();
                    progressBar.setValue(done * 100 / total);
                    progressLabel.setText("Fetching " + done + " / " + total + "\u2026");
                }
            }

            @Override
            protected void done() {
                try {
                    List<FetchedResult> allResults = get();
                    long ok = allResults.stream().filter(FetchedResult::ok).count();
                    progressLabel.setText("Done \u2014 " + ok + " of "
                            + allResults.size() + " fetched successfully.");
                    progressBar.setValue(100);
                    if (onImportComplete != null) {
                        onImportComplete.accept(allResults);
                    }
                } catch (CancellationException ignored) {
                    progressLabel.setText("Import cancelled.");
                } catch (Exception e) {
                    progressLabel.setText("Error: " + e.getMessage());
                }
                closeButton.setEnabled(true);
            }

        }.execute();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static BigDecimal getDisplayPrice(FetchedResult r) {
        Card card = r.card();
        return switch (r.parsed().finish) {
            case "F", "S" -> card.hasFoilPrice()   ? card.getFoilPriceAsBigDecimal()   : BigDecimal.ZERO;
            case "E"      -> card.hasEtchedPrice()  ? card.getEtchedPriceAsBigDecimal() : BigDecimal.ZERO;
            default       -> card.hasNormalPrice()  ? card.getPriceAsBigDecimal()       : BigDecimal.ZERO;
        };
    }

    private static String finishTag(String finish) {
        return switch (finish) {
            case "F" -> " (Foil)";
            case "E" -> " (Etched)";
            case "S" -> " (Surge Foil)";
            default  -> "";
        };
    }
}
