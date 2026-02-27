package com.cardpricer.gui.panel;

import com.cardpricer.gui.ShortcutHelpDialog;
import com.cardpricer.model.BountyCard;
import com.cardpricer.model.BuyRateRule;
import com.cardpricer.service.BuyRateService;
import com.formdev.flatlaf.*;
import com.formdev.flatlaf.themes.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Panel for application preferences and settings.
 */
public class PreferencesPanel extends JPanel {

    private static final String   HELP_TITLE = "Preferences — Help";
    private static final String[] HELP_COLS  = {"Setting", "Description"};
    private static final String[][] HELP_ROWS = {
        {"--- Appearance", ""},
        {"Theme",           "Select and apply a UI colour theme instantly"},
        {"--- Network", ""},
        {"Shared Folder",   "Path to a network folder shared across workstations"},
        {"Browse",          "Pick the shared folder via a folder chooser"},
        {"Test Connection", "Verify the folder exists and is writable"},
        {"Save",            "Persist the shared folder path"},
        {"--- Buy Rates — Rules", ""},
        {"Min Price ($)",   "Cards above this value use this row's rates"},
        {"Credit % / Check %", "Payout percentages for this price tier"},
        {"$0.00 row",       "Catch-all rate — cannot be removed"},
        {"Save Rules",      "Persist rule changes to disk"},
        {"--- Buy Rates — Bounties", ""},
        {"Add Bounty",      "Override buy rate for a specific card by name"},
        {"Import CSV",      "Bulk-import bounties: CARD NAME,CREDIT%,CHECK%"},
        {"Import and Add",  "Merge CSV rows — existing names overwritten"},
        {"Import and Replace", "Clear all bounties first, then import"},
        {"Export CSV",      "Export current bounty table to a CSV file"},
        {"Save Bounties",   "Persist bounty changes to disk"},
    };

    /**
     * Functional interface for a theme-application action that may throw a checked exception.
     */
    @FunctionalInterface
    private interface ThemeApplier {
        void apply() throws Exception;
    }

    private static final Preferences prefs = Preferences.userNodeForPackage(PreferencesPanel.class);
    private static final String THEME_KEY              = "app.theme";
    static final         String SHARED_FOLDER_KEY      = "shared.trades.folder";

    /**
     * Registry mapping theme display names to their application actions.
     * Evaluated lazily at class-load time; new themes can be added here without
     * modifying {@link #applyThemeByName(String)}.
     */
    private static final Map<String, ThemeApplier> THEME_REGISTRY = new LinkedHashMap<>();

    static {
        // Core FlatLaf themes
        THEME_REGISTRY.put("FlatLaf Dark",        FlatDarkLaf::setup);
        THEME_REGISTRY.put("FlatLaf Light",       FlatLightLaf::setup);
        THEME_REGISTRY.put("FlatLaf Darcula",     FlatDarculaLaf::setup);
        THEME_REGISTRY.put("FlatLaf IntelliJ",    FlatIntelliJLaf::setup);
        THEME_REGISTRY.put("FlatLaf macOS Dark",  FlatMacDarkLaf::setup);
        THEME_REGISTRY.put("FlatLaf macOS Light", FlatMacLightLaf::setup);

        // IntelliJ Themes Pack (requires flatlaf-intellij-themes.jar)
        THEME_REGISTRY.put("Arc",                     () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcIJTheme"));
        THEME_REGISTRY.put("Arc Orange",              () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme"));
        THEME_REGISTRY.put("Arc Dark",                () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme"));
        THEME_REGISTRY.put("Arc Dark Orange",         () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme"));
        THEME_REGISTRY.put("Carbon",                  () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme"));
        THEME_REGISTRY.put("Cobalt 2",                () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatCobalt2IJTheme"));
        THEME_REGISTRY.put("Cyan Light",              () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme"));
        THEME_REGISTRY.put("Dark Flat",               () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme"));
        THEME_REGISTRY.put("Dark Purple",             () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme"));
        THEME_REGISTRY.put("Dracula",                 () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme"));
        THEME_REGISTRY.put("Gradianto Dark Fuchsia",  () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoDarkFuchsiaIJTheme"));
        THEME_REGISTRY.put("Gradianto Deep Ocean",    () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme"));
        THEME_REGISTRY.put("Gradianto Midnight Blue", () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme"));
        THEME_REGISTRY.put("Gradianto Nature Green",  () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoNatureGreenIJTheme"));
        THEME_REGISTRY.put("Gruvbox Dark Hard",       () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme"));
        THEME_REGISTRY.put("Gruvbox Dark Medium",     () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkMediumIJTheme"));
        THEME_REGISTRY.put("Gruvbox Dark Soft",       () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkSoftIJTheme"));
        THEME_REGISTRY.put("Hiberbee Dark",           () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme"));
        THEME_REGISTRY.put("High Contrast",           () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme"));
        THEME_REGISTRY.put("Light Flat",              () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme"));
        THEME_REGISTRY.put("Material Design Dark",    () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatMaterialDesignDarkIJTheme"));
        THEME_REGISTRY.put("Monocai",                 () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatMonocaiIJTheme"));
        THEME_REGISTRY.put("Monokai Pro",             () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme"));
        THEME_REGISTRY.put("Nord",                    () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatNordIJTheme"));
        THEME_REGISTRY.put("One Dark",                () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme"));
        THEME_REGISTRY.put("Solarized Dark",          () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme"));
        THEME_REGISTRY.put("Solarized Light",         () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme"));
        THEME_REGISTRY.put("Spacegray",               () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme"));
        THEME_REGISTRY.put("Vuesion",                 () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme"));
    }

    // Available themes (derived from registry keys to keep a single source of truth)
    private static final String[] THEMES = THEME_REGISTRY.keySet().toArray(new String[0]);

    private JComboBox<String> themeCombo;
    private JButton applyButton;
    private JTextField sharedFolderField;

    // Buy Rates tab state
    private final BuyRateService buyRateService = new BuyRateService();
    private DefaultTableModel rulesTableModel;
    private JLabel buyRatesStatusLabel;
    private DefaultTableModel bountyTableModel;
    private JTable bountyTable;
    private JLabel bountiesStatusLabel;

    /** Constructs the preferences panel and initialises the Appearance and Network tabs. */
    public PreferencesPanel() {
        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createTabbedSettings(), BorderLayout.CENTER);
    }

    private JPanel createTopPanel() {
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Preferences");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Customize your application settings");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));

        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subtitle);

        JButton helpBtn = new JButton("?");
        helpBtn.setFocusPainted(false);
        helpBtn.setPreferredSize(new Dimension(34, 34));
        helpBtn.setFont(helpBtn.getFont().deriveFont(Font.BOLD, 14f));
        helpBtn.setToolTipText("Help");
        helpBtn.addActionListener(e ->
                ShortcutHelpDialog.show(SwingUtilities.getWindowAncestor(this),
                        HELP_TITLE, HELP_COLS, HELP_ROWS));

        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.add(titlePanel, BorderLayout.CENTER);
        panel.add(helpBtn,    BorderLayout.EAST);

        return panel;
    }

    private JTabbedPane createTabbedSettings() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Appearance", createAppearanceTab());
        tabs.addTab("Network",    createNetworkTab());
        tabs.addTab("Buy Rates",  createBuyRatesTab());
        return tabs;
    }

    private JPanel createAppearanceTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 10, 10, 10));

        JPanel themeSection = createThemeSection();
        themeSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(themeSection);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel createNetworkTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 10, 10, 10));

        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Shared Trades Folder"),
                new EmptyBorder(12, 12, 12, 12)
        ));
        section.setMaximumSize(new Dimension(700, 180));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: label + field + Browse button
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        section.add(new JLabel("Folder path:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        sharedFolderField = new JTextField(prefs.get(SHARED_FOLDER_KEY, ""), 28);
        sharedFolderField.setToolTipText("e.g. \\\\PC-NAME\\Trades");
        section.add(sharedFolderField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseBtn = new JButton("Browse...");
        browseBtn.setFocusPainted(false);
        browseBtn.addActionListener(e -> browseForSharedFolder());
        section.add(browseBtn, gbc);

        // Row 1: help text
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        JLabel helpLabel = new JLabel("<html>Set to a shared network folder (e.g. <code>\\\\PC-NAME\\Trades</code>) so all computers read/write the same trade files.</html>");
        helpLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11f));
        section.add(helpLabel, gbc);

        // Row 2: Test + Save buttons
        gbc.gridy = 2; gbc.gridwidth = 3;
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);

        JButton saveBtn = new JButton("Save");
        saveBtn.setFocusPainted(false);
        saveBtn.addActionListener(e -> saveSharedFolder());

        JButton testBtn = new JButton("Test Connection");
        testBtn.setFocusPainted(false);
        testBtn.addActionListener(e -> testSharedFolder());

        btnRow.add(saveBtn);
        btnRow.add(testBtn);
        section.add(btnRow, gbc);

        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(section);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void browseForSharedFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Shared Trades Folder");
        String current = sharedFolderField.getText().trim();
        if (!current.isEmpty()) {
            File dir = new File(current);
            if (dir.exists()) chooser.setCurrentDirectory(dir);
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            sharedFolderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void saveSharedFolder() {
        String path = sharedFolderField.getText().trim();
        prefs.put(SHARED_FOLDER_KEY, path);
        JOptionPane.showMessageDialog(this,
                "Shared folder saved:\n" + (path.isEmpty() ? "(none)" : path),
                "Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private void testSharedFolder() {
        String path = sharedFolderField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No folder path entered.", "Test", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Path does not exist or is not a folder:\n" + path,
                    "Test Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Try writing a temp file to confirm write access
        try {
            Path tmp = Files.createTempFile(dir.toPath(), ".occ_test_", ".tmp");
            Files.delete(tmp);
            JOptionPane.showMessageDialog(this, "Connection OK — folder is accessible and writable.",
                    "Test Passed", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Folder exists but is not writable:\n" + ex.getMessage(),
                    "Test Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Returns the configured shared trades folder, or null/empty if not set. */
    public static String getSharedTradesFolder() {
        return prefs.get(SHARED_FOLDER_KEY, "");
    }

    // -------------------------------------------------------------------------
    // Buy Rates tab
    // -------------------------------------------------------------------------

    private JPanel createBuyRatesTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 10, 10, 10));

        // --- Section 1: Tiered Rules ---
        JPanel rulesSection = new JPanel(new BorderLayout(8, 8));
        rulesSection.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Tiered Buy Rates"),
                new EmptyBorder(10, 10, 10, 10)
        ));

        rulesTableModel = new DefaultTableModel(
                new String[]{"Min Price ($)", "Credit (%)", "Check (%)"}, 0) {
            @Override
            public Class<?> getColumnClass(int col) { return String.class; }
        };
        JTable rulesTable = new JTable(rulesTableModel);
        rulesTable.setRowHeight(24);
        rulesTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane rulesScroll = new JScrollPane(rulesTable);
        rulesScroll.setPreferredSize(new Dimension(500, 160));
        rulesSection.add(rulesScroll, BorderLayout.CENTER);

        // Buttons row
        JPanel rulesBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        JButton addRowBtn = new JButton("Add Row");
        addRowBtn.addActionListener(e -> rulesTableModel.addRow(new String[]{"0.00", "50", "33"}));

        JButton removeRowBtn = new JButton("Remove Selected");
        removeRowBtn.addActionListener(e -> {
            int row = rulesTable.getSelectedRow();
            if (row < 0) return;
            String minVal = (String) rulesTableModel.getValueAt(row, 0);
            try {
                if (new BigDecimal(minVal.trim()).compareTo(BigDecimal.ZERO) == 0) {
                    JOptionPane.showMessageDialog(this,
                            "The $0.00 catch-all row cannot be removed.",
                            "Protected Row", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (NumberFormatException ignored) {}
            rulesTableModel.removeRow(row);
        });

        JButton saveRulesBtn = new JButton("Save Rules");
        saveRulesBtn.addActionListener(e -> saveRules());

        buyRatesStatusLabel = new JLabel(" ");
        buyRatesStatusLabel.setFont(buyRatesStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        rulesBtns.add(addRowBtn);
        rulesBtns.add(removeRowBtn);
        rulesBtns.add(saveRulesBtn);
        rulesBtns.add(buyRatesStatusLabel);
        rulesSection.add(rulesBtns, BorderLayout.SOUTH);

        JLabel rulesHelp = new JLabel(
                "<html>Rules are evaluated from highest to lowest threshold — first match wins.<br>"
                + "The <b>$0.00</b> row is the catch-all and cannot be removed. "
                + "Rates are entered as whole percentages (e.g. 60 for 60%).</html>");
        rulesHelp.setFont(rulesHelp.getFont().deriveFont(Font.ITALIC, 11f));
        rulesHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
        rulesSection.add(rulesHelp, BorderLayout.NORTH);

        rulesSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        rulesSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        // --- Section 2: Bounty Cards ---
        JPanel bountySection = new JPanel(new BorderLayout(8, 8));
        bountySection.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Bounty Cards"),
                new EmptyBorder(10, 10, 10, 10)
        ));

        bountyTableModel = new DefaultTableModel(
                new String[]{"Card Name", "Credit (%)", "Check (%)"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };
        bountyTable = new JTable(bountyTableModel);
        bountyTable.setRowHeight(24);
        bountyTable.getTableHeader().setReorderingAllowed(false);

        // '+' key duplicates the selected row
        bountyTable.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke('+'), "duplicateRow");
        bountyTable.getActionMap().put("duplicateRow", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int row = bountyTable.getSelectedRow();
                if (row < 0) return;
                // Stop any active cell editor first
                if (bountyTable.isEditing()) bountyTable.getCellEditor().stopCellEditing();
                String name   = (String) bountyTableModel.getValueAt(row, 0);
                String credit = (String) bountyTableModel.getValueAt(row, 1);
                String check  = (String) bountyTableModel.getValueAt(row, 2);
                bountyTableModel.addRow(new String[]{name, credit, check});
                int newRow = bountyTableModel.getRowCount() - 1;
                bountyTable.setRowSelectionInterval(newRow, newRow);
                bountyTable.scrollRectToVisible(bountyTable.getCellRect(newRow, 0, true));
                bountiesStatusLabel.setText("Row duplicated — click Save Bounties to persist.");
                bountiesStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
            }
        });
        JScrollPane bountyScroll = new JScrollPane(bountyTable);
        bountyScroll.setPreferredSize(new Dimension(500, 140));
        bountySection.add(bountyScroll, BorderLayout.CENTER);

        JPanel bountyBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton addBountyBtn    = new JButton("Add Bounty");
        JButton removeBountyBtn = new JButton("Remove Selected");
        JButton importCsvBtn    = new JButton("Import CSV");
        JButton exportCsvBtn    = new JButton("Export CSV");
        JButton saveBountiesBtn = new JButton("Save Bounties");

        bountiesStatusLabel = new JLabel(" ");
        bountiesStatusLabel.setFont(bountiesStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        addBountyBtn.addActionListener(e -> showAddBountyDialog());
        removeBountyBtn.addActionListener(e -> {
            int row = bountyTable.getSelectedRow();
            if (row >= 0) {
                bountyTableModel.removeRow(row);
                bountiesStatusLabel.setText("Row removed — click Save Bounties to persist.");
                bountiesStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
            }
        });
        importCsvBtn.addActionListener(e -> importBountyCsv());
        exportCsvBtn.addActionListener(e -> exportBountiesCsv());
        saveBountiesBtn.addActionListener(e -> saveBounties());

        bountyBtns.add(addBountyBtn);
        bountyBtns.add(removeBountyBtn);
        bountyBtns.add(importCsvBtn);
        bountyBtns.add(exportCsvBtn);
        bountyBtns.add(saveBountiesBtn);
        bountyBtns.add(bountiesStatusLabel);
        bountySection.add(bountyBtns, BorderLayout.SOUTH);

        JLabel bountyHelp = new JLabel(
                "<html>Bounty cards override tier rules for <b>any printing</b> of the named card (case-insensitive).<br>"
                + "CSV import format: <code>CARD NAME,CREDIT PERCENT,CHECK PERCENT</code></html>");
        bountyHelp.setFont(bountyHelp.getFont().deriveFont(Font.ITALIC, 11f));
        bountyHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
        bountySection.add(bountyHelp, BorderLayout.NORTH);

        bountySection.setAlignmentX(Component.LEFT_ALIGNMENT);
        bountySection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        panel.add(rulesSection);
        panel.add(Box.createVerticalStrut(12));
        panel.add(bountySection);
        panel.add(Box.createVerticalGlue());

        // Populate tables from saved prefs
        loadRulesIntoTable();
        loadBountiesIntoTable();

        return panel;
    }

    /** Populates the tiered-rules table from the current BuyRateService state. */
    private void loadRulesIntoTable() {
        if (rulesTableModel == null) return;
        rulesTableModel.setRowCount(0);
        for (BuyRateRule rule : buyRateService.getRules()) {
            // Display rates as whole percentages
            String minStr    = rule.thresholdMin.toPlainString();
            String creditStr = rule.creditRate.multiply(new BigDecimal("100"))
                                   .stripTrailingZeros().toPlainString();
            String checkStr  = rule.checkRate.multiply(new BigDecimal("100"))
                                   .stripTrailingZeros().toPlainString();
            rulesTableModel.addRow(new String[]{minStr, creditStr, checkStr});
        }
    }

    /** Reads the rules table, validates, and persists via BuyRateService. */
    private void saveRules() {
        List<BuyRateRule> newRules = new ArrayList<>();
        for (int i = 0; i < rulesTableModel.getRowCount(); i++) {
            try {
                BigDecimal min    = new BigDecimal(((String) rulesTableModel.getValueAt(i, 0)).trim());
                BigDecimal credit = new BigDecimal(((String) rulesTableModel.getValueAt(i, 1)).trim())
                                        .divide(new BigDecimal("100"));
                BigDecimal check  = new BigDecimal(((String) rulesTableModel.getValueAt(i, 2)).trim())
                                        .divide(new BigDecimal("100"));
                newRules.add(new BuyRateRule(min, credit, check));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Row " + (i + 1) + " contains invalid numbers. Please correct and try again.",
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                return;
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this,
                        "Row " + (i + 1) + ": " + ex.getMessage(),
                        "Invalid Rate", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        try {
            buyRateService.saveRules(newRules);
            buyRatesStatusLabel.setText("Saved!");
            buyRatesStatusLabel.setForeground(new Color(0, 150, 0));
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Populates the bounty table from the current BuyRateService state. */
    private void loadBountiesIntoTable() {
        if (bountyTableModel == null) return;
        bountyTableModel.setRowCount(0);
        for (BountyCard b : buyRateService.getBounties()) {
            String creditStr = b.creditRate.multiply(new BigDecimal("100"))
                                    .stripTrailingZeros().toPlainString();
            String checkStr  = b.checkRate.multiply(new BigDecimal("100"))
                                    .stripTrailingZeros().toPlainString();
            bountyTableModel.addRow(new String[]{b.cardName, creditStr, checkStr});
        }
    }

    /** Opens a dialog to add a single bounty card entry. */
    private void showAddBountyDialog() {
        JTextField nameField   = new JTextField(20);
        JTextField creditField = new JTextField("60", 6);
        JTextField checkField  = new JTextField("40", 6);

        JPanel dialogPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 4, 4, 4);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        dialogPanel.add(new JLabel("Card Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        dialogPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        dialogPanel.add(new JLabel("Credit %:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        dialogPanel.add(creditField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        dialogPanel.add(new JLabel("Check %:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        dialogPanel.add(checkField, gbc);

        int result = JOptionPane.showConfirmDialog(this, dialogPanel,
                "Add Bounty Card", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Card name cannot be empty.",
                    "Invalid Input", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            new BigDecimal(creditField.getText().trim());
            new BigDecimal(checkField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Credit and Check must be valid numbers.",
                    "Invalid Input", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // F4: Check for duplicate name (case-insensitive)
        int existingRow = -1;
        for (int r = 0; r < bountyTableModel.getRowCount(); r++) {
            String existing = ((String) bountyTableModel.getValueAt(r, 0)).trim();
            if (existing.equalsIgnoreCase(name)) {
                existingRow = r;
                break;
            }
        }
        if (existingRow >= 0) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    "A bounty for \"" + name + "\" already exists.\nOverwrite it?",
                    "Duplicate Bounty",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (overwrite == JOptionPane.YES_OPTION) {
                bountyTableModel.setValueAt(name,                      existingRow, 0);
                bountyTableModel.setValueAt(creditField.getText().trim(), existingRow, 1);
                bountyTableModel.setValueAt(checkField.getText().trim(),  existingRow, 2);
                bountyTable.setRowSelectionInterval(existingRow, existingRow);
                bountyTable.scrollRectToVisible(bountyTable.getCellRect(existingRow, 0, true));
                bountiesStatusLabel.setText("Row updated — click Save Bounties to persist.");
                bountiesStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
            }
            return;
        }

        bountyTableModel.addRow(new String[]{name, creditField.getText().trim(), checkField.getText().trim()});
        int newRow = bountyTableModel.getRowCount() - 1;
        bountyTable.setRowSelectionInterval(newRow, newRow);
        bountyTable.scrollRectToVisible(bountyTable.getCellRect(newRow, 0, true));
        bountiesStatusLabel.setText("Row added — click Save Bounties to persist.");
        bountiesStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
    }

    /** Opens a CSV file chooser, parses the file, then merges or replaces the bounty table. */
    private void importBountyCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Bounty CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV / Text files (*.csv, *.txt)", "csv", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        List<BountyCard> parsed;
        try {
            parsed = buyRateService.parseBountyCsv(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to parse CSV:\n" + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (parsed.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No valid bounty rows found in the file.",
                    "Import", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Ask user: Add (merge) or Replace
        String[] options = {"Import and Add", "Import and Replace", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                "Found " + parsed.size() + " bounty row(s) in the file.\n"
                + "How should they be imported?",
                "Import Mode",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) return;

        if (choice == 1) {
            // Import and Replace — clear table first
            bountyTableModel.setRowCount(0);
        }

        // Merge: build a name→row map of existing entries so incoming overwrites duplicates
        if (choice == 0) {
            // Build index of existing rows by name (upper-cased)
            java.util.Map<String, Integer> existingIndex = new java.util.HashMap<>();
            for (int r = 0; r < bountyTableModel.getRowCount(); r++) {
                String existing = ((String) bountyTableModel.getValueAt(r, 0)).toUpperCase();
                existingIndex.put(existing, r);
            }
            for (BountyCard b : parsed) {
                String creditStr = b.creditRate.multiply(new BigDecimal("100"))
                                        .stripTrailingZeros().toPlainString();
                String checkStr  = b.checkRate.multiply(new BigDecimal("100"))
                                        .stripTrailingZeros().toPlainString();
                Integer existRow = existingIndex.get(b.cardName.toUpperCase());
                if (existRow != null) {
                    bountyTableModel.setValueAt(b.cardName, existRow, 0);
                    bountyTableModel.setValueAt(creditStr,  existRow, 1);
                    bountyTableModel.setValueAt(checkStr,   existRow, 2);
                } else {
                    bountyTableModel.addRow(new String[]{b.cardName, creditStr, checkStr});
                }
            }
        } else {
            // Replace — table already cleared, just add all
            for (BountyCard b : parsed) {
                String creditStr = b.creditRate.multiply(new BigDecimal("100"))
                                        .stripTrailingZeros().toPlainString();
                String checkStr  = b.checkRate.multiply(new BigDecimal("100"))
                                        .stripTrailingZeros().toPlainString();
                bountyTableModel.addRow(new String[]{b.cardName, creditStr, checkStr});
            }
        }

        bountiesStatusLabel.setText("Imported " + parsed.size() + " row(s) — click Save Bounties to persist.");
        bountiesStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
    }

    /** Reads the bounty table rows and persists them via BuyRateService. */
    private void saveBounties() {
        List<BountyCard> newBounties = new ArrayList<>();
        for (int i = 0; i < bountyTableModel.getRowCount(); i++) {
            try {
                String name      = ((String) bountyTableModel.getValueAt(i, 0)).trim();
                BigDecimal credit = new BigDecimal(((String) bountyTableModel.getValueAt(i, 1)).trim())
                                        .divide(new BigDecimal("100"));
                BigDecimal check  = new BigDecimal(((String) bountyTableModel.getValueAt(i, 2)).trim())
                                        .divide(new BigDecimal("100"));
                if (name.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "Row " + (i + 1) + ": card name is empty.",
                            "Invalid Input", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                newBounties.add(new BountyCard(name, credit, check));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Row " + (i + 1) + " contains invalid numbers. Please correct and try again.",
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        buyRateService.saveBounties(newBounties);
        loadBountiesIntoTable();
        bountiesStatusLabel.setText("Saved & sorted alphabetically.");
        bountiesStatusLabel.setForeground(new Color(0, 150, 0));
    }

    /** Exports the current bounty table contents to a user-chosen CSV file. */
    private void exportBountiesCsv() {
        if (bountyTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No bounties to export.",
                    "Export CSV", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Bounties CSV");
        chooser.setSelectedFile(new File("bounties.csv"));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getAbsolutePath() + ".csv");
        }

        StringBuilder sb = new StringBuilder("CARD NAME,CREDIT PERCENT,CHECK PERCENT\n");
        for (int i = 0; i < bountyTableModel.getRowCount(); i++) {
            String name   = ((String) bountyTableModel.getValueAt(i, 0)).replace(",", ";");
            String credit = (String) bountyTableModel.getValueAt(i, 1);
            String check  = (String) bountyTableModel.getValueAt(i, 2);
            sb.append(name).append(',').append(credit).append(',').append(check).append('\n');
        }

        try {
            Files.writeString(file.toPath(), sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            bountiesStatusLabel.setText("Exported to " + file.getName());
            bountiesStatusLabel.setForeground(new Color(0, 150, 0));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createThemeSection() {
        JPanel section = new JPanel(new BorderLayout(10, 10));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Appearance"),
                new EmptyBorder(15, 15, 15, 15)
        ));
        section.setMaximumSize(new Dimension(600, 150));

        JPanel content = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Theme selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        JLabel themeLabel = new JLabel("Theme:");
        content.add(themeLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        themeCombo = new JComboBox<>(THEMES);
        themeCombo.setPreferredSize(new Dimension(250, 32));

        // Load saved theme
        String savedTheme = prefs.get(THEME_KEY, "FlatLaf Dark");
        themeCombo.setSelectedItem(savedTheme);

        content.add(themeCombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        applyButton = new JButton("Apply");
        applyButton.setFocusPainted(false);
        applyButton.setPreferredSize(new Dimension(100, 32));
        applyButton.addActionListener(e -> applyTheme());
        content.add(applyButton, gbc);

        // Info label
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        JLabel infoLabel = new JLabel("Theme will be applied instantly to all windows");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC, 11f));
        content.add(infoLabel, gbc);

        section.add(content, BorderLayout.CENTER);

        return section;
    }

    private void applyTheme() {
        String selectedTheme = (String) themeCombo.getSelectedItem();

        try {
            // Save preference
            prefs.put(THEME_KEY, selectedTheme);

            // Apply theme immediately
            applyThemeByName(selectedTheme);

            // Update all open windows
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }

            JOptionPane.showMessageDialog(
                    this,
                    "Theme applied successfully!",
                    "Theme Changed",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to apply theme: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Returns the persisted theme display name, defaulting to {@code "FlatLaf Dark"}.
     *
     * @return saved theme name
     */
    public static String getSavedTheme() {
        return prefs.get(THEME_KEY, "FlatLaf Dark");
    }

    /**
     * Reads the saved theme preference and applies it immediately.
     * Intended to be called before the main frame is built so that the correct
     * Look-and-Feel is in place before any components are created.
     */
    public static void applySavedTheme() {
        String theme = getSavedTheme();
        applyThemeByName(theme);
    }

    /**
     * Applies a theme by name using {@link #THEME_REGISTRY}.
     * Falls back to FlatLaf Dark if the requested theme is not found or fails to load.
     *
     * @param themeName display name of the theme to apply
     */
    public static void applyThemeByName(String themeName) {
        ThemeApplier applier = THEME_REGISTRY.getOrDefault(themeName, FlatDarkLaf::setup);
        try {
            applier.apply();
        } catch (Exception e) {
            // Fallback to dark theme if the selected theme is not available
            System.err.println("Failed to apply theme '" + themeName + "': " + e.getMessage());
            System.err.println("Note: IntelliJ themes require flatlaf-intellij-themes.jar in classpath");
            FlatDarkLaf.setup();
        }
    }
}