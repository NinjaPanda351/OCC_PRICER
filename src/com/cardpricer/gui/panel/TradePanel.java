package com.cardpricer.gui.panel;

import com.cardpricer.exception.ScryfallApiException;
import com.cardpricer.gui.dialog.CardSearchDialog;
import com.cardpricer.model.Card;
import com.cardpricer.model.TradeItem;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.service.TradeReceivingExportService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Quick-entry panel for receiving cards from trades/purchases
 * Format: "TDM 3" (normal), "TDM 3F" (foil), "TDM 3E" (etched)
 */
public class TradePanel extends JPanel {

    private final ScryfallApiService apiService;
    private final TradeReceivingExportService exportService;

    private List<TradeItem> receivedCards;
    private List<String> cardConditions; // Track condition for each card

    // Input field
    private JTextField cardCodeField;
    private JLabel cardPreviewLabel;

    // Table
    private JTable cardTable;
    private DefaultTableModel tableModel;

    // Summary labels
    private JLabel totalPriceLabel;
    private JLabel halfRateLabel;
    private JLabel thirdRateLabel;

    // Trade info fields
    private JTextField traderNameField;
    private JTextField customerNameField;
    private JTextField driversLicenseField;
    private JTextField checkNumberField;
    private JRadioButton storeCreditRadio;
    private JRadioButton checkRadio;

    // Preview card
    private Card previewCard;
    private String previewFinish;

    // Condition options
    private static final String[] CONDITIONS = {"NM", "LP", "MP", "HP", "DMG"};

    // Condition multipliers (NM = 1.0, each tier = 0.8 of previous)
    private static final double[] CONDITION_MULTIPLIERS = {
            1.0,    // NM - Market Price
            0.8,    // LP - 80% of NM
            0.64,   // MP - 80% of LP (0.8 * 0.8)
            0.512,  // HP - 80% of MP (0.8 * 0.8 * 0.8)
            0.4096  // DMG - 80% of HP (0.8 * 0.8 * 0.8 * 0.8)
    };

    public TradePanel() {
        this.apiService = new ScryfallApiService();
        this.exportService = new TradeReceivingExportService();
        this.receivedCards = new ArrayList<>();
        this.cardConditions = new ArrayList<>();

        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(createInputPanel(), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        SwingUtilities.invokeLater(() -> cardCodeField.requestFocusInWindow());
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Card Entry"),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // Top: Trade information fields
        JPanel tradeInfoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 1: Trader Name and Customer Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        tradeInfoPanel.add(new JLabel("Trader Name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        traderNameField = new JTextField(15);
        tradeInfoPanel.add(traderNameField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        tradeInfoPanel.add(new JLabel("Customer Name:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 1.0;
        customerNameField = new JTextField(15);
        tradeInfoPanel.add(customerNameField, gbc);

        // Row 2: Driver's License and Check Number
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        tradeInfoPanel.add(new JLabel("Driver's License:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        driversLicenseField = new JTextField(15);
        tradeInfoPanel.add(driversLicenseField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        tradeInfoPanel.add(new JLabel("Check Number:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 1.0;
        checkNumberField = new JTextField(15);
        checkNumberField.setEnabled(false); // Disabled by default
        tradeInfoPanel.add(checkNumberField, gbc);

        // Row 3: Payment Method
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        tradeInfoPanel.add(new JLabel("Payment Method:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        JPanel paymentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));

        ButtonGroup paymentGroup = new ButtonGroup();
        storeCreditRadio = new JRadioButton("Store Credit (50%)");
        checkRadio = new JRadioButton("Check (33%)");

        storeCreditRadio.setSelected(true); // Default to store credit

        paymentGroup.add(storeCreditRadio);
        paymentGroup.add(checkRadio);

        // Add listeners to enable/disable check number field and highlight rate
        storeCreditRadio.addActionListener(e -> {
            checkNumberField.setEnabled(false);
            checkNumberField.setText("");
            highlightPaymentRate();
        });

        checkRadio.addActionListener(e -> {
            checkNumberField.setEnabled(true);
            checkNumberField.requestFocusInWindow();
            highlightPaymentRate();
        });

        paymentPanel.add(storeCreditRadio);
        paymentPanel.add(checkRadio);

        tradeInfoPanel.add(paymentPanel, gbc);

        panel.add(tradeInfoPanel, BorderLayout.NORTH);

        // Middle: Input field
        JPanel inputPanel = new JPanel(new BorderLayout(10, 5));

        JLabel instructionLabel = new JLabel("<html>Enter card code and press ENTER (e.g., TDM 3, TDM 3F, TDM 3E) | Type <b>misc</b> for manual entry | Press <b>Ctrl+F</b> to search by name</html>");
        instructionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        inputPanel.add(instructionLabel, BorderLayout.NORTH);

        cardCodeField = new JTextField();
        cardCodeField.setFont(cardCodeField.getFont().deriveFont(Font.PLAIN, 20f));
        cardCodeField.setToolTipText("Type set code + number, press Enter to add");

        cardCodeField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Format first, then search and add
                    String input = cardCodeField.getText();
                    ParsedCode parsed = parseCardCode(input);
                    if (parsed != null) {
                        String formatted = formatCardCode(parsed);
                        cardCodeField.setText(formatted);
                    }

                    // Now fetch if we don't have a preview yet, then add
                    if (previewCard == null) {
                        fetchPreviewAndAdd();
                    } else {
                        addCard();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_F && e.isControlDown()) {
                    // Ctrl+F to open search dialog
                    openSearchDialog();
                } else {
                    schedulePreview();
                }
            }
        });

        inputPanel.add(cardCodeField, BorderLayout.CENTER);

        panel.add(inputPanel, BorderLayout.CENTER);

        // Bottom: Preview
        cardPreviewLabel = new JLabel("Enter a card code above...");
        cardPreviewLabel.setFont(cardPreviewLabel.getFont().deriveFont(Font.BOLD, 14f));
        cardPreviewLabel.setBorder(new EmptyBorder(10, 5, 5, 5));
        panel.add(cardPreviewLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Table with checkbox and Condition column
        String[] columns = {"☑", "Code", "Card Name", "Condition", "Price"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 3 || column == 4; // Checkbox, Condition and Price columns are editable
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) {
                    return Boolean.class; // Checkbox column
                } else if (column == 3) {
                    return String.class; // Condition column
                }
                return super.getColumnClass(column);
            }
        };

        cardTable = new JTable(tableModel);
        cardTable.setFont(cardTable.getFont().deriveFont(14f));
        cardTable.setRowHeight(32);
        cardTable.getColumnModel().getColumn(0).setPreferredWidth(40);  // Checkbox
        cardTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Code
        cardTable.getColumnModel().getColumn(2).setPreferredWidth(310); // Card Name
        cardTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Condition
        cardTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Price

        // Enable table sorting
        cardTable.setAutoCreateRowSorter(true);

        // Configure row sorter for proper price sorting
        javax.swing.table.TableRowSorter<DefaultTableModel> sorter =
                new javax.swing.table.TableRowSorter<>(tableModel);
        cardTable.setRowSorter(sorter);

        // Custom comparator for the Price column (column 4, was 3)
        sorter.setComparator(4, new java.util.Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                try {
                    // Remove $ and parse as BigDecimal for proper numeric sorting
                    BigDecimal price1 = new BigDecimal(s1.replace("$", "").replace(",", "").trim());
                    BigDecimal price2 = new BigDecimal(s2.replace("$", "").replace(",", "").trim());
                    return price1.compareTo(price2);
                } catch (Exception e) {
                    // Fallback to string comparison if parsing fails
                    return s1.compareTo(s2);
                }
            }
        });

        // Set up condition dropdown
        JComboBox<String> conditionCombo = new JComboBox<>(CONDITIONS);
        cardTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(conditionCombo));

        // Add listener to condition dropdown to update price when changed
        conditionCombo.addActionListener(e -> {
            if (cardTable.isEditing()) {
                int row = cardTable.getEditingRow();
                if (row >= 0) {
                    SwingUtilities.invokeLater(() -> {
                        updatePriceForCondition(row);
                        updateSummary();
                    });
                }
            }
        });

        // Add cell editor that formats price on commit
        cardTable.getColumnModel().getColumn(4).setCellEditor(new javax.swing.DefaultCellEditor(new JTextField()) {
            @Override
            public boolean stopCellEditing() {
                String value = (String) getCellEditorValue();
                try {
                    // Parse and format the price
                    value = value.replace("$", "").replace(",", "").trim();
                    BigDecimal price = new BigDecimal(value);

                    if (price.compareTo(BigDecimal.ZERO) <= 0) {
                        JOptionPane.showMessageDialog(cardTable,
                                "Price must be greater than $0.00",
                                "Invalid Price",
                                JOptionPane.WARNING_MESSAGE);
                        return false;
                    }

                    // Set formatted value
                    ((JTextField) getComponent()).setText(String.format("$%.2f", price));
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(cardTable,
                            "Invalid price format. Please enter a valid number.",
                            "Invalid Price",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }

                boolean result = super.stopCellEditing();
                if (result) {
                    // Update summary after successful edit
                    SwingUtilities.invokeLater(() -> updateSummary());
                }
                return result;
            }
        });

        // Add right-click context menu
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Delete Card");
        deleteItem.addActionListener(e -> {
            int row = cardTable.getSelectedRow();
            if (row >= 0) {
                // Convert view row to model row since table might be sorted
                int modelRow = cardTable.convertRowIndexToModel(row);
                String cardName = (String) tableModel.getValueAt(modelRow, 2);
                int confirm = JOptionPane.showConfirmDialog(
                        cardTable,
                        "Delete \"" + cardName + "\"?",
                        "Confirm Delete",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    receivedCards.remove(modelRow);
                    cardConditions.remove(modelRow);
                    tableModel.removeRow(modelRow);
                    updateSummary();
                }
            }
        });
        contextMenu.add(deleteItem);

        cardTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            private void showContextMenu(java.awt.event.MouseEvent e) {
                int row = cardTable.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    cardTable.setRowSelectionInterval(row, row);
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(cardTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Received Cards (Click column headers to sort)"));

        panel.add(scrollPane, BorderLayout.CENTER);

        // Button panel with selection controls and remove button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        JButton selectAllBtn = new JButton("Select All");
        selectAllBtn.setFocusPainted(false);
        selectAllBtn.setPreferredSize(new Dimension(100, 32));
        selectAllBtn.addActionListener(e -> selectAllCards(true));

        JButton deselectAllBtn = new JButton("Deselect All");
        deselectAllBtn.setFocusPainted(false);
        deselectAllBtn.setPreferredSize(new Dimension(110, 32));
        deselectAllBtn.addActionListener(e -> selectAllCards(false));

        JButton removeSelectedBtn = new JButton("Delete Selected");
        removeSelectedBtn.setFocusPainted(false);
        removeSelectedBtn.setPreferredSize(new Dimension(130, 32));
        removeSelectedBtn.addActionListener(e -> removeSelectedCards());

        buttonPanel.add(selectAllBtn);
        buttonPanel.add(deselectAllBtn);
        buttonPanel.add(removeSelectedBtn);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Left: Action buttons in 2x2 grid with modern styling
        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 10, 8));

        JButton searchBtn = new JButton("Search by Name (Ctrl+F)");
        JButton clearBtn = new JButton("Clear All");
        JButton exportInventoryBtn = new JButton("Export to POS (CSV)");
        JButton saveListBtn = new JButton("Save Card List");

        // Modern flat button styling
        for (JButton btn : new JButton[]{searchBtn, clearBtn, exportInventoryBtn, saveListBtn}) {
            btn.setFocusPainted(false);
            btn.setPreferredSize(new Dimension(180, 36));
        }

        searchBtn.addActionListener(e -> openSearchDialog());
        clearBtn.addActionListener(e -> clearAll());
        exportInventoryBtn.addActionListener(e -> exportToPOS());
        saveListBtn.addActionListener(e -> saveList());

        buttonPanel.add(searchBtn);
        buttonPanel.add(clearBtn);
        buttonPanel.add(exportInventoryBtn);
        buttonPanel.add(saveListBtn);

        panel.add(buttonPanel, BorderLayout.WEST);

        // Right: Summary
        JPanel summaryPanel = new JPanel();
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
        summaryPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Value Summary"),
                new EmptyBorder(10, 15, 10, 15)
        ));
        summaryPanel.setPreferredSize(new Dimension(280, 120));

        totalPriceLabel = new JLabel("TOTAL PRICE: $0.00");
        totalPriceLabel.setFont(totalPriceLabel.getFont().deriveFont(Font.BOLD, 18f));
        totalPriceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        halfRateLabel = new JLabel("HALF RATE (50%): $0.00");
        halfRateLabel.setFont(halfRateLabel.getFont().deriveFont(Font.PLAIN, 14f));
        halfRateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        thirdRateLabel = new JLabel("THIRD RATE (33%): $0.00");
        thirdRateLabel.setFont(thirdRateLabel.getFont().deriveFont(Font.PLAIN, 14f));
        thirdRateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        summaryPanel.add(totalPriceLabel);
        summaryPanel.add(Box.createVerticalStrut(8));
        summaryPanel.add(halfRateLabel);
        summaryPanel.add(Box.createVerticalStrut(5));
        summaryPanel.add(thirdRateLabel);

        panel.add(summaryPanel, BorderLayout.EAST);

        // Highlight the initial rate
        highlightPaymentRate();

        return panel;
    }

    private void highlightPaymentRate() {
        // Reset both to normal
        halfRateLabel.setFont(halfRateLabel.getFont().deriveFont(Font.PLAIN, 14f));
        thirdRateLabel.setFont(thirdRateLabel.getFont().deriveFont(Font.PLAIN, 14f));
        halfRateLabel.setForeground(UIManager.getColor("Label.foreground"));
        thirdRateLabel.setForeground(UIManager.getColor("Label.foreground"));

        // Highlight the selected rate
        if (storeCreditRadio.isSelected()) {
            halfRateLabel.setFont(halfRateLabel.getFont().deriveFont(Font.BOLD, 16f));
            halfRateLabel.setForeground(new Color(0, 150, 0));
        } else if (checkRadio.isSelected()) {
            thirdRateLabel.setFont(thirdRateLabel.getFont().deriveFont(Font.BOLD, 16f));
            thirdRateLabel.setForeground(new Color(0, 150, 0));
        }
    }

    private Timer previewTimer;

    private void schedulePreview() {
        if (previewTimer != null) {
            previewTimer.stop();
        }

        previewTimer = new Timer(500, e -> fetchPreview());
        previewTimer.setRepeats(false);
        previewTimer.start();
    }

    private void fetchPreview() {
        String input = cardCodeField.getText();
        if (input == null || input.trim().isEmpty() || input.trim().length() < 3) {
            clearPreview();
            return;
        }

        ParsedCode parsed = parseCardCode(input);
        if (parsed == null) {
            clearPreview();
            return;
        }

        new SwingWorker<Card, Void>() {
            @Override
            protected Card doInBackground() throws Exception {
                return apiService.fetchCard(parsed.setCode, parsed.collectorNumber);
            }

            @Override
            protected void done() {
                try {
                    Card card = get();
                    displayPreview(card, parsed.finish);
                } catch (Exception e) {
                    clearPreview();
                }
            }
        }.execute();
    }

    private void fetchPreviewAndAdd() {
        String input = cardCodeField.getText();
        if (input == null || input.trim().isEmpty()) {
            return;
        }

        // Check if user typed "misc" for manual entry
        if (input.trim().equalsIgnoreCase("misc")) {
            promptForMiscCard();
            return;
        }

        ParsedCode parsed = parseCardCode(input);
        if (parsed == null) {
            JOptionPane.showMessageDialog(this,
                    "Invalid card code format.\nUse: SET NUMBER or SET NUMBERF\nOr type 'misc' for manual entry",
                    "Invalid Format",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        cardPreviewLabel.setText("Loading...");

        new SwingWorker<Card, Void>() {
            @Override
            protected Card doInBackground() throws Exception {
                return apiService.fetchCard(parsed.setCode, parsed.collectorNumber);
            }

            @Override
            protected void done() {
                try {
                    Card card = get();
                    previewCard = card;
                    previewFinish = parsed.finish;
                    displayPreview(card, parsed.finish);
                    addCard();
                } catch (Exception e) {
                    // Check if it's a card not found error
                    if (e.getMessage() != null && e.getMessage().contains("not found")) {
                        // Card not found, prompt for manual entry
                        promptForManualPrice(parsed);
                    } else {
                        JOptionPane.showMessageDialog(TradePanel.this,
                                "Failed to fetch card: " + e.getMessage(),
                                "API Error",
                                JOptionPane.ERROR_MESSAGE);
                        clearPreview();
                    }
                }
            }
        }.execute();
    }

    private void promptForManualPrice(ParsedCode parsed) {
        String priceInput = JOptionPane.showInputDialog(this,
                String.format("Card %s %s not found in Scryfall.\nEnter manual price:",
                        parsed.setCode, parsed.collectorNumber),
                "Manual Price Entry",
                JOptionPane.QUESTION_MESSAGE);

        if (priceInput == null || priceInput.trim().isEmpty()) {
            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();
            return;
        }

        try {
            priceInput = priceInput.replace("$", "").replace(",", "").trim();
            BigDecimal price = new BigDecimal(priceInput);

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Price must be greater than $0.00",
                        "Invalid Price",
                        JOptionPane.WARNING_MESSAGE);
                cardCodeField.setText("");
                clearPreview();
                cardCodeField.requestFocusInWindow();
                return;
            }

            String code = parsed.setCode + " " + parsed.collectorNumber;
            if (!parsed.finish.isEmpty()) {
                code += parsed.finish;
            }

            // Add to table with NM condition by default
            tableModel.addRow(new Object[]{
                    false,  // Checkbox unchecked by default
                    code,
                    "Misc Magic Card",
                    "NM",
                    String.format("$%.2f", price)
            });

            // Create a dummy TradeItem to keep receivedCards in sync
            Card miscCard = new Card();
            miscCard.setName("Misc Magic Card");
            miscCard.setSetCode("MISC");
            miscCard.setCollectorNumber("1");
            miscCard.setRarity("common");
            miscCard.setPrice(price.toString());

            TradeItem item = new TradeItem(miscCard, false, 1);
            receivedCards.add(item);
            cardConditions.add("NM");

            updateSummary();

            // Scroll to the newly added row
            int lastRow = cardTable.getRowCount() - 1;
            cardTable.scrollRectToVisible(cardTable.getCellRect(lastRow, 0, true));

            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Invalid price format. Please enter a valid number.",
                    "Invalid Price",
                    JOptionPane.ERROR_MESSAGE);
            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();
        }
    }

    /**
     * Prompts user to add a misc card with custom name and price
     */
    private void promptForMiscCard() {
        // Create a dialog for misc card entry
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField nameField = new JTextField(30);
        JTextField priceField = new JTextField(10);

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Card Name:"), gbc);

        gbc.gridx = 1;
        panel.add(nameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Price:"), gbc);

        gbc.gridx = 1;
        panel.add(priceField, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Add Misc Card", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();
            return;
        }

        String cardName = nameField.getText().trim();
        String priceInput = priceField.getText().trim();

        if (cardName.isEmpty()) {
            cardName = "Misc Magic Card";
        }

        if (priceInput.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Price is required",
                    "Missing Price",
                    JOptionPane.WARNING_MESSAGE);
            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();
            return;
        }

        try {
            priceInput = priceInput.replace("$", "").replace(",", "").trim();
            BigDecimal price = new BigDecimal(priceInput);

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Price must be greater than $0.00",
                        "Invalid Price",
                        JOptionPane.WARNING_MESSAGE);
                cardCodeField.setText("");
                clearPreview();
                cardCodeField.requestFocusInWindow();
                return;
            }

            // Add to table with NM condition by default
            tableModel.addRow(new Object[]{
                    false,  // Checkbox unchecked by default
                    "MISC",
                    cardName,
                    "NM",
                    String.format("$%.2f", price)
            });

            // Create a dummy TradeItem to keep receivedCards in sync
            Card miscCard = new Card();
            miscCard.setName(cardName);
            miscCard.setSetCode("MISC");
            miscCard.setCollectorNumber("1");
            miscCard.setRarity("common");
            miscCard.setPrice(price.toString());

            TradeItem item = new TradeItem(miscCard, false, 1);
            receivedCards.add(item);
            cardConditions.add("NM");

            updateSummary();

            // Scroll to the newly added row
            int lastRow = cardTable.getRowCount() - 1;
            cardTable.scrollRectToVisible(cardTable.getCellRect(lastRow, 0, true));

            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Invalid price format. Please enter a valid number.",
                    "Invalid Price",
                    JOptionPane.ERROR_MESSAGE);
            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();
        }
    }

    private void displayPreview(Card card, String finish) {
        previewCard = card;
        previewFinish = finish;

        StringBuilder text = new StringBuilder();
        text.append("✓ ").append(card.getName());

        if (card.getFrameEffectDisplay() != null) {
            text.append(" - ").append(card.getFrameEffectDisplay());
        }

        BigDecimal price;
        String finishName;

        if ("F".equals(finish)) {
            price = card.getFoilPriceAsBigDecimal();
            finishName = "Foil";
        } else if ("E".equals(finish)) {
            price = card.getFoilPriceAsBigDecimal();
            finishName = "Etched";
        } else {
            price = card.getPriceAsBigDecimal();
            finishName = "Normal";
        }

        BigDecimal roundedPrice = applyPricingRules(price, card.getRarity());

        text.append(String.format(" (%s) - $%.2f [%s %s]",
                finishName, roundedPrice, card.getSetCode(), capitalize(card.getRarity())));

        cardPreviewLabel.setText(text.toString());
        cardPreviewLabel.setForeground(price.compareTo(BigDecimal.ZERO) > 0 ?
                new Color(0, 120, 0) : Color.RED);
    }

    private void clearPreview() {
        previewCard = null;
        previewFinish = null;
        cardPreviewLabel.setText("Enter a card code above...");
        cardPreviewLabel.setForeground(UIManager.getColor("Label.foreground"));
    }

    private void addCard() {
        if (previewCard == null) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a valid card code",
                    "No Card",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean isFoil = "F".equals(previewFinish) || "E".equals(previewFinish);

        if (isFoil && !previewCard.hasFoilPrice()) {
            JOptionPane.showMessageDialog(this,
                    "This card has no foil price available",
                    "No Price",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!isFoil && !previewCard.hasNormalPrice()) {
            JOptionPane.showMessageDialog(this,
                    "This card has no normal price available",
                    "No Price",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        TradeItem item = new TradeItem(previewCard, isFoil, 1);
        receivedCards.add(item);
        cardConditions.add("NM"); // Default to NM condition

        Card card = item.getCard();
        String code = card.getSetCode() + " " + card.getCollectorNumber();

        if (isFoil) {
            code += "E".equals(previewFinish) ? "E" : "F";
        }

        StringBuilder name = new StringBuilder(card.getName());
        if (card.getFrameEffectDisplay() != null) {
            name.append(" - ").append(card.getFrameEffectDisplay());
        }
        if (isFoil) {
            name.append(" (").append("E".equals(previewFinish) ? "Etched" : "Foil").append(")");
        }

        BigDecimal roundedPrice = applyPricingRules(item.getUnitPrice(), card.getRarity());

        tableModel.addRow(new Object[]{
                false,  // Checkbox unchecked by default
                code,
                name.toString(),
                "NM", // Default condition
                String.format("$%.2f", roundedPrice)
        });

        updateSummary();

        // Scroll to the newly added row (bottom)
        int lastRow = cardTable.getRowCount() - 1;
        cardTable.scrollRectToVisible(cardTable.getCellRect(lastRow, 0, true));

        cardCodeField.setText("");
        clearPreview();
        cardCodeField.requestFocusInWindow();
    }

    private void removeSelected() {
        int row = cardTable.getSelectedRow();
        if (row >= 0) {
            // Convert view row to model row since table might be sorted
            int modelRow = cardTable.convertRowIndexToModel(row);
            receivedCards.remove(modelRow);
            cardConditions.remove(modelRow);
            tableModel.removeRow(modelRow);
            updateSummary();
        }
    }

    /**
     * Selects or deselects all checkboxes
     */
    private void selectAllCards(boolean selected) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(selected, i, 0); // Column 0 is checkbox
        }
    }

    /**
     * Removes all cards that have their checkbox checked
     */
    private void removeSelectedCards() {
        // Build list of model rows that are checked
        List<Integer> rowsToDelete = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean checked = (Boolean) tableModel.getValueAt(i, 0);
            if (checked != null && checked) {
                rowsToDelete.add(i);
            }
        }

        if (rowsToDelete.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No cards selected for deletion",
                    "Nothing to Delete",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Delete %d selected card(s)?", rowsToDelete.size()),
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            // Remove in reverse order to maintain indices
            for (int i = rowsToDelete.size() - 1; i >= 0; i--) {
                int row = rowsToDelete.get(i);
                receivedCards.remove(row);
                cardConditions.remove(row);
                tableModel.removeRow(row);
            }
            updateSummary();
        }
    }

    /**
     * Updates the price for a specific row based on its condition
     */
    private void updatePriceForCondition(int row) {
        // Convert view row to model row since table might be sorted
        int modelRow = cardTable.convertRowIndexToModel(row);

        if (modelRow < 0 || modelRow >= receivedCards.size()) {
            return;
        }

        String condition = (String) tableModel.getValueAt(modelRow, 3); // Column 3 is Condition
        cardConditions.set(modelRow, condition);

        TradeItem item = receivedCards.get(modelRow);
        Card card = item.getCard();

        // Get base price (already rounded by pricing rules)
        BigDecimal basePrice = applyPricingRules(item.getUnitPrice(), card.getRarity());

        // Apply condition multiplier
        BigDecimal conditionPrice = applyConditionMultiplier(basePrice, condition);

        // Update the price in the table (Column 4 is Price)
        tableModel.setValueAt(String.format("$%.2f", conditionPrice), modelRow, 4);
    }

    /**
     * Applies condition multiplier to a price and re-applies rounding rules
     */
    private BigDecimal applyConditionMultiplier(BigDecimal basePrice, String condition) {
        int conditionIndex = getConditionIndex(condition);
        double multiplier = CONDITION_MULTIPLIERS[conditionIndex];

        BigDecimal adjustedPrice = basePrice.multiply(BigDecimal.valueOf(multiplier));

        // Re-apply rounding rules to the condition-adjusted price
        if (adjustedPrice.compareTo(BigDecimal.TEN) < 0) {
            // Below $10 - round to nearest $0.50
            BigDecimal rounded = adjustedPrice.divide(new BigDecimal("0.5"), 0, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("0.5"));
            return rounded;
        } else {
            // $10 and above - round to nearest $1.00
            return adjustedPrice.setScale(0, RoundingMode.HALF_UP);
        }
    }

    /**
     * Gets the index of a condition in the CONDITIONS array
     */
    private int getConditionIndex(String condition) {
        for (int i = 0; i < CONDITIONS.length; i++) {
            if (CONDITIONS[i].equals(condition)) {
                return i;
            }
        }
        return 0; // Default to NM
    }

    private void updateSummary() {
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < receivedCards.size(); i++) {
            // Get price directly from table (which may have been manually edited or adjusted by condition)
            String priceStr = (String) tableModel.getValueAt(i, 4); // Column 4 is Price
            priceStr = priceStr.replace("$", "").trim();

            try {
                BigDecimal price = new BigDecimal(priceStr);
                TradeItem item = receivedCards.get(i);
                total = total.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
            } catch (Exception e) {
                // If parsing fails, use the original price
                TradeItem item = receivedCards.get(i);
                Card card = item.getCard();
                BigDecimal roundedPrice = applyPricingRules(item.getUnitPrice(), card.getRarity());
                total = total.add(roundedPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }

        BigDecimal halfRate = total.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal thirdRate = total.multiply(new BigDecimal("0.33")).setScale(2, RoundingMode.HALF_UP);

        int cardCount = receivedCards.stream().mapToInt(TradeItem::getQuantity).sum();
        totalPriceLabel.setText(String.format("TOTAL: $%.2f (%d cards)", total, cardCount));
        halfRateLabel.setText(String.format("HALF RATE (50%%): $%.2f", halfRate));
        thirdRateLabel.setText(String.format("THIRD RATE (33%%): $%.2f", thirdRate));
    }

    private void clearAll() {
        if (receivedCards.isEmpty()) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "Clear all " + receivedCards.size() + " cards?",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            receivedCards.clear();
            cardConditions.clear();
            tableModel.setRowCount(0);
            updateSummary();
            cardCodeField.requestFocusInWindow();
        }
    }

    private void exportToPOS() {
        if (receivedCards.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No cards to export",
                    "Empty List",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String traderName = traderNameField.getText().trim();
        if (traderName.isEmpty()) {
            traderName = "Unknown";
        }

        // Filter out MISC cards for POS export
        List<TradeItem> nonMiscCards = new ArrayList<>();
        int miscCount = 0;
        for (TradeItem item : receivedCards) {
            if (!"MISC".equals(item.getCard().getSetCode())) {
                nonMiscCards.add(item);
            } else {
                miscCount++;
            }
        }

        if (nonMiscCards.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No cards to export - all cards are MISC cards.\n" +
                            "MISC cards are excluded from POS import.",
                    "Nothing to Export",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String filename = exportService.exportToPOSFormat(nonMiscCards, traderName);

            String message = String.format("POS import file created!\n\n" +
                            "File: %s\n" +
                            "Cards Exported: %d\n" +
                            "Total Value: $%.2f\n\n" +
                            "Ready to import into your POS system.",
                    filename,
                    nonMiscCards.size(),
                    calculateTotalValue(nonMiscCards));

            if (miscCount > 0) {
                message += String.format("\n\nNote: %d MISC card(s) were excluded from export.", miscCount);
            }

            JOptionPane.showMessageDialog(this,
                    message,
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Export failed: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void saveList() {
        if (receivedCards.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No cards to save",
                    "Empty List",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String traderName = traderNameField.getText().trim();
        String customerName = customerNameField.getText().trim();
        String driversLicense = driversLicenseField.getText().trim();
        String checkNumber = checkNumberField.getText().trim();
        boolean isStoreCredit = storeCreditRadio.isSelected();

        if (customerName.isEmpty()) {
            customerName = "Unknown";
        }

        try {
            String filename = exportService.saveCardList(
                    receivedCards,
                    traderName,
                    customerName,
                    driversLicense,
                    checkNumber,
                    isStoreCredit,
                    cardConditions
            );

            JOptionPane.showMessageDialog(this,
                    String.format("Card list saved!\n\nFile: %s", filename),
                    "Saved",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Save failed: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private BigDecimal getTotalValue() {
        return calculateTotalValue(receivedCards);
    }

    /**
     * Calculates total value for a list of trade items
     */
    private BigDecimal calculateTotalValue(List<TradeItem> items) {
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < receivedCards.size(); i++) {
            // Only calculate if this item is in the provided list
            TradeItem receivedItem = receivedCards.get(i);
            if (!items.contains(receivedItem)) {
                continue;
            }

            // Get price from table (may have been manually edited)
            String priceStr = (String) tableModel.getValueAt(i, 4); // Column 4 is Price
            priceStr = priceStr.replace("$", "").trim();

            try {
                BigDecimal price = new BigDecimal(priceStr);
                TradeItem item = receivedCards.get(i);
                total = total.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
            } catch (Exception e) {
                // Fallback to original price
                TradeItem item = receivedCards.get(i);
                Card card = item.getCard();
                BigDecimal roundedPrice = applyPricingRules(item.getUnitPrice(), card.getRarity());
                total = total.add(roundedPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }

        return total;
    }

    private BigDecimal applyPricingRules(BigDecimal price, String rarity) {
        BigDecimal minimum = getMinimumByRarity(rarity);
        BigDecimal priceToRound = price.max(minimum);

        if (priceToRound.compareTo(BigDecimal.TEN) < 0) {
            BigDecimal rounded = priceToRound.divide(new BigDecimal("0.5"), 0, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("0.5"));
            return rounded.max(minimum);
        } else {
            return priceToRound.setScale(0, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal getMinimumByRarity(String rarity) {
        if (rarity == null) {
            return new BigDecimal("0.10");
        }

        String rarityLower = rarity.toLowerCase();

        if (rarityLower.equals("rare") || rarityLower.equals("mythic")) {
            return new BigDecimal("0.50");
        } else if (rarityLower.equals("uncommon")) {
            return new BigDecimal("0.25");
        } else {
            return new BigDecimal("0.10");
        }
    }

    private ParsedCode parseCardCode(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        input = input.trim().toUpperCase().replaceAll("\\s+", " ");

        String finish = "";
        if (input.endsWith("F")) {
            finish = "F";
            input = input.substring(0, input.length() - 1).trim();
        } else if (input.endsWith("E")) {
            finish = "E";
            input = input.substring(0, input.length() - 1).trim();
        }

        String[] parts = input.split(" ");
        if (parts.length < 2) {
            return null;
        }

        String setCode = parts[0].toUpperCase();
        String collectorNumber = parts[1];

        if (parts.length == 1 && parts[0].length() > 3) {
            String combined = parts[0];
            if (combined.length() >= 4) {
                setCode = combined.substring(0, 3);
                collectorNumber = combined.substring(3);
            }
        }

        return new ParsedCode(setCode, collectorNumber, finish);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String formatCardCode(ParsedCode parsed) {
        StringBuilder formatted = new StringBuilder();
        formatted.append(parsed.setCode.toUpperCase())
                .append(" ")
                .append(parsed.collectorNumber);

        if (!parsed.finish.isEmpty()) {
            formatted.append(parsed.finish.toUpperCase());
        }

        return formatted.toString();
    }

    private void openSearchDialog() {
        CardSearchDialog dialog = new CardSearchDialog(
                SwingUtilities.getWindowAncestor(this),
                apiService
        );
        dialog.setVisible(true);

        Card selectedCard = dialog.getSelectedCard();
        String selectedFinish = dialog.getSelectedFinish();

        if (selectedCard != null) {
            previewCard = selectedCard;
            previewFinish = selectedFinish;
            displayPreview(selectedCard, selectedFinish);

            String code = selectedCard.getSetCode() + " " + selectedCard.getCollectorNumber();
            if (selectedFinish != null && !selectedFinish.isEmpty()) {
                code += selectedFinish;
            }
            cardCodeField.setText(code);

            cardCodeField.requestFocusInWindow();
        }
    }

    private static class ParsedCode {
        String setCode;
        String collectorNumber;
        String finish;

        ParsedCode(String setCode, String collectorNumber, String finish) {
            this.setCode = setCode;
            this.collectorNumber = collectorNumber;
            this.finish = finish;
        }
    }
}