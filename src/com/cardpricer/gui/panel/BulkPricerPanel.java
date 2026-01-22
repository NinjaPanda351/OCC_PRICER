package com.cardpricer.gui.panel;

import com.cardpricer.model.Card;
import com.cardpricer.service.CsvExportService;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.util.SetList;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Panel for bulk set price fetching with multi-set selection
 */
public class BulkPricerPanel extends JPanel {

    private final ScryfallApiService apiService;
    private final CsvExportService csvService;

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

    public BulkPricerPanel() {
        this.apiService = new ScryfallApiService();
        this.csvService = new CsvExportService();
        this.setCheckboxes = new HashMap<>();
        this.visibleCheckboxes = new ArrayList<>();

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        initializeSetCheckboxes();
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Set Pricer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Select sets to fetch prices from Scryfall/TCGPlayer");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(title);
        panel.add(Box.createVerticalStrut(4));
        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(16));

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Left side - Set selector
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.4;
        gbc.weighty = 1.0;
        panel.add(createSetSelectorPanel(), gbc);

        // Right side - Configuration and log
        gbc.gridx = 1;
        gbc.weightx = 0.6;
        panel.add(createRightPanel(), gbc);

        return panel;
    }

    private JPanel createSetSelectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE, 2),
                "Select Sets",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                null,
                Color.WHITE
        ));

        // Search and control panel
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));

        // Search field
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        JLabel searchLabel = new JLabel("Search:");
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchField = new JTextField();
        searchField.setToolTipText("Type to filter sets...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterSets(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterSets(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterSets(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Control buttons
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton selectAllBtn = new JButton("Select All");
        JButton deselectAllBtn = new JButton("Deselect All");

        selectAllBtn.setFocusPainted(false);
        deselectAllBtn.setFocusPainted(false);

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

        return panel;
    }

    private void initializeSetCheckboxes() {
        List<String> allSets = SetList.ALL_SETS_CUSTOM_CODES;

        for (String setCode : allSets) {
            JCheckBox checkbox = new JCheckBox(setCode);
            checkbox.setAlignmentX(Component.LEFT_ALIGNMENT);
            checkbox.addActionListener(e -> updateSelectedCount());

            setCheckboxes.put(setCode, checkbox);
            visibleCheckboxes.add(checkbox);
            setCheckboxPanel.add(checkbox);
        }

        updateSelectedCount();
    }

    private void filterSets() {
        String searchText = searchField.getText().toLowerCase().trim();

        setCheckboxPanel.removeAll();
        visibleCheckboxes.clear();

        for (String setCode : SetList.ALL_SETS_CUSTOM_CODES) {
            if (searchText.isEmpty() || setCode.toLowerCase().contains(searchText)) {
                JCheckBox checkbox = setCheckboxes.get(setCode);
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

        // Maintain SetList order
        for (String setCode : SetList.ALL_SETS_CUSTOM_CODES) {
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
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE, 2),
                "Export Configuration",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                null,
                Color.WHITE
        ));

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
        JPanel splitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(10000, 1000, 100000, 1000);
        splitSizeSpinner = new JSpinner(spinnerModel);
        splitSizeSpinner.setToolTipText("Number of cards per combined file");
        ((JSpinner.DefaultEditor) splitSizeSpinner.getEditor()).getTextField().setColumns(6);

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
        fetchButton = new JButton("Fetch Selected Sets");
        fetchButton.setFocusPainted(false);
        fetchButton.putClientProperty("JButton.buttonType", "roundRect");
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
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE, 2),
                "Process Log",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                null,
                Color.WHITE
        ));

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
        List<String> selectedSets = getSelectedSets();;

        // Validate all codes exist in mapping
        List<String> invalid = selectedSets.stream()
                .filter(code -> SetList.getApiCode(code).equals(code)) // No mapping found
                .toList();

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
     * Background worker for fetching multiple sets
     */
    private class BulkFetchWorker extends SwingWorker<Void, String> {
        private final List<String> sets;
        private final CsvExportService.ExportFormat format;
        private final boolean createCombined;
        private final int splitSize;
        private int successCount = 0;
        private int failureCount = 0;
        private List<com.cardpricer.model.CardEntry> allCardEntries = new ArrayList<>();

        public BulkFetchWorker(List<String> sets, CsvExportService.ExportFormat format,
                               boolean createCombined, int splitSize) {
            this.sets = sets;
            this.format = format;
            this.createCombined = createCombined;
            this.splitSize = splitSize;
        }

        @Override
        protected Void doInBackground() throws Exception {
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
                    String apiCode = SetList.getApiCode(setCode);

                    List<Card> cards = apiService.fetchCardsFromSet(apiCode);

                    for (Card card : cards) {
                        card.setSetCode(setCode);
                    }

                    String filename = setCode.toUpperCase() + "_prices.csv";
                    csvService.exportCardsToCsv(cards, filename, format);

                    if (createCombined) {
                        allCardEntries.addAll(flattenCards(cards));
                    }

                    publish("✓ " + setCode + " - Success (" + cards.size() + " cards)");
                    successCount++;

                    if (current < sets.size()) {
                        Thread.sleep(100);
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


        private void createCombinedFiles() throws Exception {
            new java.io.File("data").mkdirs();

            int fileNumber = 0;
            int currentIndex = 0;

            while (currentIndex < allCardEntries.size()) {
                int endIndex = Math.min(currentIndex + splitSize, allCardEntries.size());
                List<com.cardpricer.model.CardEntry> batch =
                        allCardEntries.subList(currentIndex, endIndex);

                String filename = String.format("data/%02d_combined_list.csv", fileNumber);

                try (java.io.PrintWriter writer = new java.io.PrintWriter(
                        new java.io.FileWriter(filename))) {

                    // Write header based on format
                    if (format == CsvExportService.ExportFormat.IMPORT_UTILITY) {
                        writer.println("DEPARTMENT,CATEGORY,CODE,DESCRIPTION,EXTENDED DESCRIPTION,SUB DESCRIPTION,TAX,PRICE");
                    }

                    // Write entries
                    for (com.cardpricer.model.CardEntry entry : batch) {
                        if (format == CsvExportService.ExportFormat.IMPORT_UTILITY) {
                            writer.println(entry.toImportUtilityRow());
                        } else if (format == CsvExportService.ExportFormat.ITEM_WIZARD_CHANGE_QTY_ZERO){
                            writer.println(entry.toZeroOutItems());
                        } else {
                            writer.println(entry.toItemWizardRow());
                        }
                    }
                }

                publish(">>> Created combined file: " + filename + " (" + batch.size() + " entries)");

                currentIndex = endIndex;
                fileNumber++;
            }

            publish("Combined files complete: " + fileNumber + " file(s) created");
        }

        private List<com.cardpricer.model.CardEntry> flattenCards(List<Card> cards) {
            List<com.cardpricer.model.CardEntry> entries = new ArrayList<>();

            for (Card card : cards) {
                // Add normal version if it has a price
                if (card.hasNormalPrice()) {
                    com.cardpricer.model.CardEntry normalEntry =
                            new com.cardpricer.model.CardEntry(card, false);
                    entries.add(normalEntry);
                }

                // Add foil version if it has a price
                if (card.hasFoilPrice()) {
                    com.cardpricer.model.CardEntry foilEntry =
                            new com.cardpricer.model.CardEntry(card, true);
                    entries.add(foilEntry);
                }
            }

            return entries;
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