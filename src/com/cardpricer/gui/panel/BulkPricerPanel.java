package com.cardpricer.gui.panel;

import com.cardpricer.gui.ShortcutHelpDialog;
import com.cardpricer.model.Card;
import com.cardpricer.service.CsvExportService;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.service.SetCatalogService;
import com.cardpricer.service.TaskCoordinator;
import com.cardpricer.util.AppTheme;
import com.cardpricer.util.CardConstants;
import com.cardpricer.util.SetList;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.awt.event.HierarchyEvent;

/**
 * Panel for bulk set price fetching with multi-set selection.
 * Implements {@link ManagedPanel} so that {@code MainSwingApplication} can determine
 * whether it is safe to unload this panel while a fetch is in progress.
 */
public class BulkPricerPanel extends JPanel implements ManagedPanel {

    private static final String   HELP_TITLE = "Set Pricer — Help";
    private static final String[] HELP_COLS  = {"Feature", "Description"};
    private static final String[][] HELP_ROWS = {
        {"Search box",      "Filter the set list by name"},
        {"Refresh sets",    "Check Scryfall for new sets now; the list also updates daily. Promo, token, Art Series, and Front Cards sets are excluded."},
        {"Check / uncheck", "Select which sets to fetch prices for"},
        {"Select All",      "Check all currently visible sets"},
        {"Deselect All",    "Uncheck all currently visible sets"},
        {"Export Format",   "Choose the output CSV format and structure"},
        {"Combined File",   "Merge all selected sets into one output file"},
        {"Fetch Prices",    "Start downloading prices from Scryfall"},
        {"Cancel",          "Stop the fetch currently in progress"},
        {"Log area",        "Shows live progress, errors, and results"},
    };

    private final ScryfallApiService apiService;
    private final CsvExportService csvService;
    private final SetCatalogService setCatalogService;
    private SetCatalogService.Catalog setCatalog = SetCatalogService.bundled();
    private SwingWorker<SetCatalogService.Catalog, SetCatalogService.Catalog> setCatalogWorker;
    private JButton refreshSetsButton;
    private JLabel setCatalogStatus;
    private final Timer setRefreshTimer = new Timer(60 * 60 * 1000, e -> refreshSets(false));

    private JTextField searchField;
    private JPanel setCheckboxPanel;
    private JScrollPane setScrollPane;
    private Map<String, JCheckBox> setCheckboxes;
    private List<JCheckBox> visibleCheckboxes;

    private JComboBox<CsvExportService.ExportFormat> formatCombo;
    private JCheckBox combinedFileCheckbox;
    private JSpinner splitSizeSpinner;
    private JTextArea logArea;
    private JButton fetchButton;
    private JButton cancelButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel selectedCountLabel;

    private SwingWorker<Void, String> currentWorker;

    /** Constructs the Set Pricer panel and builds the set-checkbox list. */
    public BulkPricerPanel() {
        this(new SetCatalogService());
    }

    BulkPricerPanel(SetCatalogService setCatalogService) {
        this.apiService = new ScryfallApiService();
        this.csvService = new CsvExportService();
        this.setCatalogService = setCatalogService;
        this.setCheckboxes = new HashMap<>();
        this.visibleCheckboxes = new ArrayList<>();

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(16, 20, 14, 20));

        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        initializeSetCheckboxes();
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    refreshSets(false);
                    setRefreshTimer.start();
                } else {
                    setRefreshTimer.stop();
                }
            }
        });
    }

    private JPanel createHeaderPanel() {
        JPanel titlePanel = AppTheme.panelHeader("Set pricer", "Price entire sets and prepare your next export.");

        JButton helpBtn = new JButton("?");


        helpBtn.setFont(helpBtn.getFont().deriveFont(Font.BOLD, 14f));
        helpBtn.setToolTipText("Help");
        helpBtn.addActionListener(e ->
                ShortcutHelpDialog.show(SwingUtilities.getWindowAncestor(this),
                        HELP_TITLE, HELP_COLS, HELP_ROWS));

        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.add(titlePanel, BorderLayout.CENTER);
        JPanel headerActions = AppTheme.transparent(new com.cardpricer.gui.WrapLayout(FlowLayout.RIGHT, 0, 0));
        headerActions.add(helpBtn);
        panel.add(headerActions, BorderLayout.EAST);
        panel.setBorder(new EmptyBorder(0, 0, 4, 0));

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel sets = createSetSelectorPanel();
        JPanel configuration = createRightPanel();
        sets.setMinimumSize(new Dimension(com.formdev.flatlaf.util.UIScale.scale(210), 0));
        configuration.setMinimumSize(new Dimension(com.formdev.flatlaf.util.UIScale.scale(320), 0));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sets, configuration) {
            private boolean initialized;
            @Override public void doLayout() {
                if (!initialized && getWidth() > 0) { setDividerLocation(.35); initialized = true; }
                super.doLayout();
            }
        };
        split.setResizeWeight(.35); split.setBorder(BorderFactory.createEmptyBorder());
        panel.add(split);
        return panel;
    }

    private JPanel createSetSelectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(AppTheme.sectionBorder("Select Sets"));

        // Search and control panel
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));

        // Search field
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        JLabel searchLabel = new JLabel("Search:");
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchField = new JTextField();
        searchField.setToolTipText("Filter by set name or code...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterSets(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterSets(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterSets(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Control buttons
        JPanel controlPanel = new JPanel(new com.cardpricer.gui.WrapLayout(FlowLayout.LEFT, 5, 0));
        JButton selectAllBtn = new JButton("Select All");
        JButton deselectAllBtn = new JButton("Deselect All");



        selectAllBtn.putClientProperty("JButton.buttonType", "roundRect");
        deselectAllBtn.putClientProperty("JButton.buttonType", "roundRect");

        selectAllBtn.addActionListener(e -> selectAllVisible(true));
        deselectAllBtn.addActionListener(e -> selectAllVisible(false));

        controlPanel.add(selectAllBtn);
        controlPanel.add(deselectAllBtn);

        selectedCountLabel = new JLabel("0 selected");
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(selectedCountLabel);

        topPanel.add(searchPanel, BorderLayout.CENTER);
        topPanel.add(controlPanel, BorderLayout.SOUTH);

        panel.add(topPanel, BorderLayout.NORTH);

        // Checkbox panel with scroll
        setCheckboxPanel = new JPanel();
        setCheckboxPanel.setLayout(new BoxLayout(setCheckboxPanel, BoxLayout.Y_AXIS));

        setScrollPane = new JScrollPane(setCheckboxPanel);
        setScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        setScrollPane.setPreferredSize(new Dimension(250, 400));

        // Increase scroll speed - 10 sets per click
        setScrollPane.getVerticalScrollBar().setUnitIncrement(160); // ~10 checkboxes (16px each)
        setScrollPane.getVerticalScrollBar().setBlockIncrement(320); // Page scroll

        panel.add(setScrollPane, BorderLayout.CENTER);

        JPanel updates = new JPanel(new BorderLayout(5, 5));
        refreshSetsButton = new JButton("Refresh sets");
        refreshSetsButton.addActionListener(e -> refreshSets(true));
        setCatalogStatus = new JLabel("Bundled list; connect to update");
        setCatalogStatus.setToolTipText("Updates daily from Scryfall; promo, token, Art Series, and Front Cards sets are excluded.");
        updates.add(refreshSetsButton, BorderLayout.NORTH);
        updates.add(setCatalogStatus, BorderLayout.SOUTH);
        panel.add(updates, BorderLayout.SOUTH);

        return panel;
    }

    private void initializeSetCheckboxes() {
        applySetCatalog(setCatalog);
    }

    void applySetCatalog(SetCatalogService.Catalog catalog) {
        List<String> selected = getSelectedSets();
        setCatalog = catalog;
        setCheckboxes.clear();
        for (SetCatalogService.Entry set : catalog.sets()) {
            JCheckBox checkbox = new JCheckBox(set.label());
            checkbox.setSelected(selected.contains(set.code()));
            checkbox.setEnabled(fetchButton.isEnabled());
            checkbox.setToolTipText(set.label() + (set.releasedAt().isBlank() ? "" : " | Release: " + set.releasedAt()));
            checkbox.setAlignmentX(Component.LEFT_ALIGNMENT);
            checkbox.addActionListener(e -> updateSelectedCount());
            setCheckboxes.put(set.code(), checkbox);
        }
        filterSets();
        String updated = catalog.updatedAt().equals(java.time.Instant.EPOCH) ? "Bundled list" : "Updated "
                + DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault()).format(catalog.updatedAt());
        setCatalogStatus.setText(catalog.notice().isBlank() ? updated : catalog.notice());
        setCatalogStatus.setToolTipText(catalog.sets().size() + " sets | " + updated
                + " | Promo, token, Art Series, and Front Cards sets excluded" + (catalog.notice().isBlank() ? "" : " | " + catalog.notice()));
    }

    private void refreshSets(boolean force) {
        if (setCatalogWorker != null) return;
        refreshSetsButton.setEnabled(false);
        setCatalogStatus.setText("Checking for new sets...");
        SetCatalogService.Catalog current = setCatalog;
        setCatalogWorker = new SwingWorker<>() {
            @Override protected SetCatalogService.Catalog doInBackground() throws Exception {
                SetCatalogService.Catalog cached = current.updatedAt().equals(java.time.Instant.EPOCH)
                        ? setCatalogService.loadCached() : current;
                publish(cached);
                return force || setCatalogService.needsRefresh(cached) ? setCatalogService.refresh() : cached;
            }
            @Override protected void process(List<SetCatalogService.Catalog> chunks) {
                applySetCatalog(chunks.getLast());
            }
            @Override protected void done() {
                try {
                    applySetCatalog(get());
                } catch (Exception failure) {
                    setCatalogStatus.setText("Update unavailable; keeping current list");
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    setCatalogStatus.setToolTipText("Refresh sets to retry. " + cause.getMessage());
                } finally {
                    setCatalogWorker = null;
                    refreshSetsButton.setEnabled(true);
                }
            }
        };
        try {
            TaskCoordinator.execute(setCatalogWorker);
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            setCatalogWorker = null;
            refreshSetsButton.setEnabled(true);
            setCatalogStatus.setText("App busy; refresh sets to retry");
        }
    }

    private void filterSets() {
        String searchText = searchField.getText().toLowerCase(Locale.ROOT).trim();

        setCheckboxPanel.removeAll();
        visibleCheckboxes.clear();

        for (SetCatalogService.Entry set : setCatalog.sets()) {
            if (set.matches(searchText)) {
                JCheckBox checkbox = setCheckboxes.get(set.code());
                visibleCheckboxes.add(checkbox);
                setCheckboxPanel.add(checkbox);
            }
        }

        setCheckboxPanel.revalidate();
        setCheckboxPanel.repaint();

        updateSelectedCount();
    }

    private void selectAllVisible(boolean selected) {
        for (JCheckBox checkbox : visibleCheckboxes) {
            checkbox.setSelected(selected);
        }
        updateSelectedCount();
    }

    private void updateSelectedCount() {
        int count = 0;
        for (JCheckBox checkbox : setCheckboxes.values()) {
            if (checkbox.isSelected()) {
                count++;
            }
        }
        selectedCountLabel.setText(count + " selected");
    }

    private List<String> getSelectedSets() {
        List<String> selected = new ArrayList<>();

        for (SetCatalogService.Entry set : setCatalog.sets()) {
            String setCode = set.code();
            JCheckBox checkbox = setCheckboxes.get(setCode);
            if (checkbox != null && checkbox.isSelected()) {
                selected.add(setCode);
            }
        }

        return selected;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        panel.add(createConfigPanel(), BorderLayout.NORTH);
        panel.add(createLogPanel(), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(AppTheme.sectionBorder("Export Configuration"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Export Format
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        JLabel formatLabel = new JLabel("Export Format:");
        panel.add(formatLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formatCombo = new JComboBox<>(CsvExportService.ExportFormat.values());
        formatCombo.setMinimumSize(new Dimension(100, formatCombo.getPreferredSize().height));
        formatCombo.setToolTipText("Choose export format for CSV");
        panel.add(formatCombo, gbc);

        // Combined file option
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        combinedFileCheckbox = new JCheckBox("Create combined files");
        combinedFileCheckbox.setSelected(true);
        combinedFileCheckbox.addActionListener(e -> {
            splitSizeSpinner.setEnabled(combinedFileCheckbox.isSelected());
        });
        panel.add(combinedFileCheckbox, gbc);

        // Split size option
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        JLabel splitLabel = new JLabel("    Split every:");
        panel.add(splitLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JPanel splitPanel = new JPanel(new com.cardpricer.gui.WrapLayout(FlowLayout.LEFT, 5, 0));

        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(10_000, 1_000, 1_000_000, 1_000);
        splitSizeSpinner = new JSpinner(spinnerModel);
        splitSizeSpinner.setToolTipText("Number of cards per combined file (1,000 to 1,000,000)");
        ((JSpinner.DefaultEditor) splitSizeSpinner.getEditor()).getTextField().setColumns(9);

        JLabel cardsLabel = new JLabel("cards");

        splitPanel.add(splitSizeSpinner);
        splitPanel.add(cardsLabel);

        panel.add(splitPanel, gbc);

        // Fetch button
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        fetchButton = AppTheme.primaryButton("Fetch Selected Sets");
        fetchButton.setFont(fetchButton.getFont().deriveFont(Font.BOLD, 14f));
        fetchButton.addActionListener(e -> fetchMultipleSets());
        panel.add(fetchButton, gbc);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setText("Ready to fetch card prices...\nSelect sets and click 'Fetch Selected Sets'\n");

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(AppTheme.sectionBorder("Process Log"));

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");

        // Status label
        statusLabel = new JLabel("Ready to fetch prices");
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel statusPanel = new JPanel(new BorderLayout(8, 8));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);

        panel.add(statusPanel, BorderLayout.CENTER);

        // Cancel button
        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelFetch());
        panel.add(cancelButton, BorderLayout.EAST);

        return panel;
    }

    private void fetchMultipleSets() {
        List<String> selectedSets = getSelectedSets();

        if (selectedSets.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select at least one set",
                    "No Sets Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        CsvExportService.ExportFormat format =
                (CsvExportService.ExportFormat) formatCombo.getSelectedItem();
        boolean createCombined = combinedFileCheckbox.isSelected();
        int splitSize = (Integer) splitSizeSpinner.getValue();

        // Confirm action
        String combinedInfo = createCombined ?
                "Yes (split at " + splitSize + " cards)" : "No";

        int result = JOptionPane.showConfirmDialog(this,
                "Fetch prices for " + selectedSets.size() + " set(s)?\n" +
                        "This may take several minutes.\n\n" +
                        "Export format: " + format + "\n" +
                        "Combined files: " + combinedInfo,
                "Confirm Fetch",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        // Disable controls
        setControlsEnabled(false);
        cancelButton.setEnabled(true);
        progressBar.setValue(0);
        progressBar.setMaximum(selectedSets.size());

        logArea.append("\n=== Starting bulk fetch ===\n");
        logArea.append("Sets selected: " + selectedSets.size() + "\n");
        logArea.append("Export format: " + format + "\n");
        logArea.append("Combined files: " + combinedInfo + "\n\n");

        // Create background worker
        currentWorker = new BulkFetchWorker(selectedSets, format, createCombined, splitSize);

        currentWorker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int percent = (Integer) evt.getNewValue();
                statusLabel.setText("Fetching prices... " + percent + "%");
            }
        });

        currentWorker.execute();

    }

    private void cancelFetch() {
        if (currentWorker != null && !currentWorker.isDone()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Cancel the current operation?",
                    "Cancel",
                    JOptionPane.YES_NO_OPTION);

            if (result == JOptionPane.YES_OPTION) {
                currentWorker.cancel(true);
                logArea.append("\n=== Cancelled by user ===\n");
                statusLabel.setText("Cancelled");
            }
        }
    }

    private void setControlsEnabled(boolean enabled) {
        searchField.setEnabled(enabled);
        formatCombo.setEnabled(enabled);
        combinedFileCheckbox.setEnabled(enabled);
        splitSizeSpinner.setEnabled(enabled && combinedFileCheckbox.isSelected());
        fetchButton.setEnabled(enabled);

        for (JCheckBox checkbox : setCheckboxes.values()) {
            checkbox.setEnabled(enabled);
        }
    }

    /**
     * Returns {@code true} when no fetch operation is running, meaning the panel
     * can safely be removed from the component tree without interrupting work.
     */
    @Override
    public boolean isSafeToUnload() {
        return (currentWorker == null || currentWorker.isDone()) && setCatalogWorker == null;
    }

    /**
     * Background worker that fetches card prices for a list of sets from Scryfall,
     * exports individual per-set CSV files, and optionally creates combined split files.
     */
    private class BulkFetchWorker extends SwingWorker<Void, String> {
        private final List<String> sets;
        private final Map<String, String> apiSetCodes;
        private final CsvExportService.ExportFormat format;
        private final boolean createCombined;
        private final int splitSize;
        private int successCount = 0;
        private int failureCount = 0;
        private List<com.cardpricer.model.CardEntry> allCardEntries = new ArrayList<>();

        /**
         * Creates the worker.
         *
         * @param sets          list of custom set codes to fetch
         * @param format        CSV export format to use for each set file
         * @param createCombined whether to produce merged combined files
         * @param splitSize     maximum number of card entries per combined file
         */
        public BulkFetchWorker(List<String> sets, CsvExportService.ExportFormat format,
                               boolean createCombined, int splitSize) {
            this.sets = sets;
            this.apiSetCodes = new HashMap<>();
            for (SetCatalogService.Entry set : setCatalog.sets()) {
                apiSetCodes.put(set.code(), set.apiCode());
            }
            this.format = format;
            this.createCombined = createCombined;
            this.splitSize = splitSize;
        }

        @Override
        protected Void doInBackground() throws Exception {
            runDirectory = com.cardpricer.util.AppDataDirectory.root().toPath().resolve("bulk-runs/"+java.util.UUID.randomUUID());
            java.nio.file.Files.createDirectories(runDirectory);
            com.cardpricer.util.AtomicFiles.write(runDirectory.resolve("manifest.json"),new org.json.JSONObject()
                    .put("status","running").put("sets",sets).put("format",format.name()).toString(2));
            for (int i = 0; i < sets.size(); i++) {
                if (isCancelled()) break;

                String setCode = sets.get(i);
                int current = i + 1;

                // SwingWorker progress MUST be 0–100 (percent)
                int percent = (int) Math.round((current * 100.0) / sets.size());
                setProgress(percent);

                // Publish both progress + log message
                publish("__PROGRESS__:" + current);
                publish(String.format("[%d/%d] Processing %s...", current, sets.size(), setCode));

                try {
                    // Snapshot the provider identity so a refresh cannot change the export.
                    String apiSetCode = apiSetCodes.get(setCode);
                    List<Card> cards = apiService.fetchCardsFromSet(apiSetCode);

                    String filename = setCode.toUpperCase() + "_prices.csv";
                    new CsvExportService(runDirectory).exportCardsToCsv(cards, filename, format);

                    if (createCombined) {
                        allCardEntries.addAll(CsvExportService.flattenCards(cards));
                    }

                    publish("✓ " + setCode + " - Success (" + cards.size() + " cards)");
                    successCount++;

                    if (current < sets.size()) {
                        Thread.sleep(CardConstants.API_RATE_LIMIT_MS);
                    }

                } catch (Exception e) {
                    publish("✗ " + setCode + " - Failed: " + e.getMessage());
                    failureCount++;
                    e.printStackTrace();
                }
            }

            if (createCombined && !allCardEntries.isEmpty() && !isCancelled()) {
                publish("\n=== Creating combined files ===");
                createCombinedFiles();
            }

            return null;
        }


        private java.nio.file.Path runDirectory;

        private void createCombinedFiles() throws Exception {
            java.io.File combinedFileDir = runDirectory.resolve("combined").toFile();
            java.nio.file.Files.createDirectories(combinedFileDir.toPath());

            int fileNumber = 0;
            int currentIndex = 0;

            while (currentIndex < allCardEntries.size()) {
                int endIndex = Math.min(currentIndex + splitSize, allCardEntries.size());
                List<com.cardpricer.model.CardEntry> batch =
                        allCardEntries.subList(currentIndex, endIndex);

                String filename = String.format("%s/%02d_combined_list.csv",
                        combinedFileDir.getAbsolutePath(), fileNumber);

                try (java.io.Writer writer = java.nio.file.Files.newBufferedWriter(
                        java.nio.file.Path.of(filename), java.nio.charset.StandardCharsets.UTF_8)) {
                    com.cardpricer.service.CardCsvEncoder.write(writer, batch, format);
                }

                publish(">>> Created combined file: " + filename + " (" + batch.size() + " entries)");

                currentIndex = endIndex;
                fileNumber++;
            }

            publish("Combined files complete: " + fileNumber + " file(s) created");
        }



        @Override
        protected void process(List<String> chunks) {
            for (String message : chunks) {

                // Progress marker
                if (message.startsWith("__PROGRESS__:")) {
                    int current = Integer.parseInt(
                            message.substring("__PROGRESS__:".length())
                    );

                    progressBar.setValue(current);
                    progressBar.setString(current + " / " + sets.size());
                    continue;
                }

                logArea.append(message + "\n");
            }

            logArea.setCaretPosition(logArea.getDocument().getLength());
        }

        @Override
        protected void done() {
            String outcome="complete";
            try { get(); if (failureCount>0) outcome="partial failure"; }
            catch (java.util.concurrent.CancellationException e) { outcome="cancelled"; }
            catch (Exception e) { outcome="failed"; logArea.append("Output failed: "+e.getMessage()+"\n"); }
            if (runDirectory!=null) try {
                com.cardpricer.util.AtomicFiles.write(runDirectory.resolve("manifest.json"),new org.json.JSONObject()
                        .put("status",outcome).put("sets",sets).put("format",format.name()).put("successful",successCount)
                        .put("failed",failureCount).put("entries",allCardEntries.size()).toString(2));
            } catch (Exception e) { outcome="failed to record manifest"; }
            if (!"complete".equals(outcome)) {
                progressBar.setString(outcome); logArea.append("Run "+outcome+"\n");
                setControlsEnabled(true); cancelButton.setEnabled(false); currentWorker=null; statusLabel.setText(outcome); return;
            }
            logArea.append("Output directory: "+runDirectory+"\n");
            progressBar.setValue(progressBar.getMaximum());
            progressBar.setString("Complete");
            cancelButton.setEnabled(false);

            String summary = String.format(
                    "\n=== Fetch Complete ===\n" +
                            "Successful: %d\n" +
                            "Failed: %d\n" +
                            "Total: %d\n" +
                            "Total card entries: %d\n",
                    successCount, failureCount, sets.size(), allCardEntries.size()
            );
            logArea.append(summary);

            if (!isCancelled()) {
                JOptionPane.showMessageDialog(BulkPricerPanel.this,
                        "Fetch complete!\n\n" +
                                "Successful: " + successCount + "\n" +
                                "Failed: " + failureCount + "\n" +
                                "Total entries: " + allCardEntries.size(),
                        "Bulk Fetch Complete",
                        JOptionPane.INFORMATION_MESSAGE);
            }

            setControlsEnabled(true);
            statusLabel.setText("Ready");
            currentWorker = null;
        }
    }
}
