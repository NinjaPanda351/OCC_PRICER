package com.cardpricer.gui.panel.prefs;

import com.cardpricer.model.BountyCard;
import com.cardpricer.model.BuyRateRule;
import com.cardpricer.service.BuyRateService;
import com.cardpricer.util.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Buy Rates tab: tiered buy-rate rules CRUD and bounty-card CRUD.
 */
public final class BuyRatesTab {

    private final BuyRateService buyRateService = BuyRateService.getInstance();

    private DefaultTableModel rulesTableModel;
    private JLabel            buyRatesStatusLabel;
    private DefaultTableModel bountyTableModel;
    private JTable            bountyTable;
    private JLabel            bountiesStatusLabel;
    private JPanel            panel; // for dialog parenting

    // ── Public API ────────────────────────────────────────────────────────────

    /** Builds and returns the Buy Rates tab panel. */
    public JPanel build() {
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 10, 10, 10));

        panel.add(buildRulesSection());
        panel.add(Box.createVerticalStrut(12));
        panel.add(buildBountySection());
        panel.add(Box.createVerticalGlue());

        loadRulesIntoTable();
        loadBountiesIntoTable();
        return panel;
    }

    // ── Rules section ─────────────────────────────────────────────────────────

    private JPanel buildRulesSection() {
        JPanel section = new JPanel(new BorderLayout(8, 8));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Tiered Buy Rates"),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel rulesHelp = new JLabel(
                "<html>Rules are evaluated from highest to lowest threshold — first match wins.<br>"
                + "The <b>$0.00</b> row is the catch-all and cannot be removed. "
                + "Rates are entered as whole percentages (e.g. 60 for 60%).</html>");
        rulesHelp.setFont(rulesHelp.getFont().deriveFont(Font.ITALIC, 11f));
        rulesHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
        section.add(rulesHelp, BorderLayout.NORTH);

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
        section.add(rulesScroll, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        JButton addRowBtn = new JButton("Add Row");
        addRowBtn.addActionListener(e -> rulesTableModel.addRow(new String[]{"0.00", "50", "33"}));

        JButton removeRowBtn = AppTheme.dangerButton("Remove Selected");
        removeRowBtn.addActionListener(e -> {
            int row = rulesTable.getSelectedRow();
            if (row < 0) return;
            String minVal = (String) rulesTableModel.getValueAt(row, 0);
            try {
                if (new BigDecimal(minVal.trim()).compareTo(BigDecimal.ZERO) == 0) {
                    JOptionPane.showMessageDialog(panel,
                            "The $0.00 catch-all row cannot be removed.",
                            "Protected Row", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (NumberFormatException ignored) {}
            rulesTableModel.removeRow(row);
        });

        JButton saveRulesBtn = AppTheme.primaryButton("Save Rules");
        saveRulesBtn.addActionListener(e -> saveRules());

        buyRatesStatusLabel = new JLabel(" ");
        buyRatesStatusLabel.setFont(buyRatesStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        btns.add(addRowBtn);
        btns.add(removeRowBtn);
        btns.add(saveRulesBtn);
        btns.add(buyRatesStatusLabel);
        section.add(btns, BorderLayout.SOUTH);

        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        return section;
    }

    private void loadRulesIntoTable() {
        if (rulesTableModel == null) return;
        rulesTableModel.setRowCount(0);
        for (BuyRateRule rule : buyRateService.getRules()) {
            String minStr    = rule.thresholdMin.toPlainString();
            String creditStr = rule.creditRate.multiply(new BigDecimal("100"))
                                   .stripTrailingZeros().toPlainString();
            String checkStr  = rule.checkRate.multiply(new BigDecimal("100"))
                                   .stripTrailingZeros().toPlainString();
            rulesTableModel.addRow(new String[]{minStr, creditStr, checkStr});
        }
    }

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
                JOptionPane.showMessageDialog(panel,
                        "Row " + (i + 1) + " contains invalid numbers. Please correct and try again.",
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                return;
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(panel,
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
            JOptionPane.showMessageDialog(panel, ex.getMessage(),
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Bounty section ────────────────────────────────────────────────────────

    private JPanel buildBountySection() {
        JPanel section = new JPanel(new BorderLayout(8, 8));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Bounty Cards"),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel bountyHelp = new JLabel(
                "<html>Bounty cards override tier rules for <b>any printing</b> of the named card (case-insensitive).<br>"
                + "CSV import format: <code>CARD NAME,CREDIT PERCENT,CHECK PERCENT</code></html>");
        bountyHelp.setFont(bountyHelp.getFont().deriveFont(Font.ITALIC, 11f));
        bountyHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
        section.add(bountyHelp, BorderLayout.NORTH);

        bountyTableModel = new DefaultTableModel(
                new String[]{"Card Name", "Credit (%)", "Check (%)"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };
        bountyTable = new JTable(bountyTableModel);
        bountyTable.setRowHeight(24);
        bountyTable.getTableHeader().setReorderingAllowed(false);

        // '+' key duplicates the selected row
        bountyTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke('+'), "duplicateRow");
        bountyTable.getActionMap().put("duplicateRow", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int row = bountyTable.getSelectedRow();
                if (row < 0) return;
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
        section.add(bountyScroll, BorderLayout.CENTER);

        bountiesStatusLabel = new JLabel(" ");
        bountiesStatusLabel.setFont(bountiesStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        JButton addBountyBtn    = new JButton("Add Bounty");
        JButton removeBountyBtn = AppTheme.dangerButton("Remove Selected");
        JButton importCsvBtn    = new JButton("Import CSV");
        JButton exportCsvBtn    = new JButton("Export CSV");
        JButton saveBountiesBtn = AppTheme.primaryButton("Save Bounties");

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

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.add(addBountyBtn);
        btns.add(removeBountyBtn);
        btns.add(importCsvBtn);
        btns.add(exportCsvBtn);
        btns.add(saveBountiesBtn);
        btns.add(bountiesStatusLabel);
        section.add(btns, BorderLayout.SOUTH);

        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        return section;
    }

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

    private void showAddBountyDialog() {
        JTextField nameField   = new JTextField(20);
        JTextField creditField = new JTextField("60", 6);
        JTextField checkField  = new JTextField("40", 6);

        JPanel dialogPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.HORIZONTAL;

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

        int result = JOptionPane.showConfirmDialog(panel, dialogPanel,
                "Add Bounty Card", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "Card name cannot be empty.",
                    "Invalid Input", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            new BigDecimal(creditField.getText().trim());
            new BigDecimal(checkField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(panel, "Credit and Check must be valid numbers.",
                    "Invalid Input", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check for duplicate name (case-insensitive)
        int existingRow = -1;
        for (int r = 0; r < bountyTableModel.getRowCount(); r++) {
            String existing = ((String) bountyTableModel.getValueAt(r, 0)).trim();
            if (existing.equalsIgnoreCase(name)) {
                existingRow = r;
                break;
            }
        }
        if (existingRow >= 0) {
            int overwrite = JOptionPane.showConfirmDialog(panel,
                    "A bounty for \"" + name + "\" already exists.\nOverwrite it?",
                    "Duplicate Bounty",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (overwrite == JOptionPane.YES_OPTION) {
                bountyTableModel.setValueAt(name,                        existingRow, 0);
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

    private void importBountyCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Bounty CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV / Text files (*.csv, *.txt)", "csv", "txt"));
        if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        List<BountyCard> parsed;
        try {
            parsed = buyRateService.parseBountyCsv(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel,
                    "Failed to parse CSV:\n" + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (parsed.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "No valid bounty rows found in the file.",
                    "Import", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] options = {"Import and Add", "Import and Replace", "Cancel"};
        int choice = JOptionPane.showOptionDialog(panel,
                "Found " + parsed.size() + " bounty row(s) in the file.\n"
                + "How should they be imported?",
                "Import Mode",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) return;

        if (choice == 1) {
            bountyTableModel.setRowCount(0);
        }

        if (choice == 0) {
            Map<String, Integer> existingIndex = new HashMap<>();
            for (int r = 0; r < bountyTableModel.getRowCount(); r++) {
                existingIndex.put(((String) bountyTableModel.getValueAt(r, 0)).toUpperCase(), r);
            }
            for (BountyCard b : parsed) {
                String creditStr = b.creditRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString();
                String checkStr  = b.checkRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString();
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
            for (BountyCard b : parsed) {
                String creditStr = b.creditRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString();
                String checkStr  = b.checkRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString();
                bountyTableModel.addRow(new String[]{b.cardName, creditStr, checkStr});
            }
        }

        bountiesStatusLabel.setText("Imported " + parsed.size() + " row(s) — click Save Bounties to persist.");
        bountiesStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
    }

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
                    JOptionPane.showMessageDialog(panel,
                            "Row " + (i + 1) + ": card name is empty.",
                            "Invalid Input", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                newBounties.add(new BountyCard(name, credit, check));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel,
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

    private void exportBountiesCsv() {
        if (bountyTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(panel, "No bounties to export.",
                    "Export CSV", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Bounties CSV");
        chooser.setSelectedFile(new File("bounties.csv"));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;

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
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
            bountiesStatusLabel.setText("Exported to " + file.getName());
            bountiesStatusLabel.setForeground(new Color(0, 150, 0));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel, "Export failed: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
