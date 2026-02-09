package com.cardpricer.gui.panel;

import com.cardpricer.exception.ScryfallApiException;
import com.cardpricer.gui.dialog.CardSearchDialog;
import com.cardpricer.model.Card;
import com.cardpricer.model.TradeItem;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.service.TradeReceivingExportService;
import com.cardpricer.util.SetList;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Quick-entry panel for receiving cards from trades/purchases
 * Format: "TDM 3" (normal), "TDM 3f" (foil), "TDM 3e" (etched)
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
    private JRadioButton inventoryRadio;
    private JRadioButton partialRadio;
    private JTextField partialCreditField;
    private JTextField partialCheckField;
    private JPanel partialPaymentPanel;

    // Preview card
    private Card previewCard;
    private String lastPreviewCode; // Track what code the preview is showing
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

        // Use BoxLayout to stack radio buttons and partial payment panel
        JPanel paymentContainer = new JPanel();
        paymentContainer.setLayout(new BoxLayout(paymentContainer, BoxLayout.Y_AXIS));

        JPanel paymentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));

        ButtonGroup paymentGroup = new ButtonGroup();
        storeCreditRadio = new JRadioButton("Store Credit (50%)");
        checkRadio = new JRadioButton("Check (33.33%)");
        inventoryRadio = new JRadioButton("Inventory (No Payout)");
        partialRadio = new JRadioButton("Partial (Split Payment)");

        storeCreditRadio.setSelected(true); // Default to store credit

        paymentGroup.add(storeCreditRadio);
        paymentGroup.add(checkRadio);
        paymentGroup.add(inventoryRadio);
        paymentGroup.add(partialRadio);

        // Add listeners to enable/disable check number field and highlight rate
        storeCreditRadio.addActionListener(e -> {
            checkNumberField.setEnabled(false);
            checkNumberField.setText("");
            partialPaymentPanel.setVisible(false);
            highlightPaymentRate();
        });

        checkRadio.addActionListener(e -> {
            checkNumberField.setEnabled(true);
            checkNumberField.requestFocusInWindow();
            partialPaymentPanel.setVisible(false);
            highlightPaymentRate();
        });

        inventoryRadio.addActionListener(e -> {
            checkNumberField.setEnabled(false);
            checkNumberField.setText("");
            partialPaymentPanel.setVisible(false);
            highlightPaymentRate();
        });

        partialRadio.addActionListener(e -> {
            checkNumberField.setEnabled(false);
            checkNumberField.setText("");
            partialPaymentPanel.setVisible(true);
            highlightPaymentRate();
            // Auto-calculate 50/50 split by default
            updatePartialSplit();
        });

        paymentPanel.add(storeCreditRadio);
        paymentPanel.add(checkRadio);
        paymentPanel.add(inventoryRadio);
        paymentPanel.add(partialRadio);

        paymentContainer.add(paymentPanel);

        // Create partial payment panel (initially hidden)
        partialPaymentPanel = createPartialPaymentPanel();
        partialPaymentPanel.setVisible(false);
        paymentContainer.add(partialPaymentPanel);

        tradeInfoPanel.add(paymentContainer, gbc);

        panel.add(tradeInfoPanel, BorderLayout.NORTH);

        // Middle: Input field
        JPanel inputPanel = new JPanel(new BorderLayout(10, 5));

        JLabel instructionLabel = new JLabel("<html>Enter card code and press ENTER (e.g., TDM 3, TDM 3f, TDM 3e) | Type <b>misc</b> for manual entry | Press <b>Ctrl+F</b> to search by name</html>");
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

                    // Check if we have a preview and if it matches the current input
                    if (previewCard != null && parsed != null) {
                        String currentCode = parsed.setCode + " " + parsed.collectorNumber;
                        // If preview doesn't match current input, clear it and fetch fresh
                        if (lastPreviewCode == null || !lastPreviewCode.equals(currentCode)) {
                            clearPreview();
                            fetchPreviewAndAdd();
                        } else {
                            // Preview matches, add it
                            addCard();
                        }
                    } else {
                        // No preview yet, fetch and add
                        fetchPreviewAndAdd();
                    }
                } else if ((e.getKeyCode() == KeyEvent.VK_F && e.isControlDown()) ||
                        e.getKeyCode() == KeyEvent.VK_F2) {
                    // Ctrl+F or F2 to open search dialog
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

        // Table with checkbox, Condition, Qty, Unit Price, and Total columns
        String[] columns = {"☑", "Code", "Card Name", "Condition", "Qty", "Unit Price", "Total"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 3 || column == 4 || column == 5; // Checkbox, Condition, Qty, Unit Price editable
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) {
                    return Boolean.class; // Checkbox column
                } else if (column == 3) {
                    return String.class; // Condition column
                } else if (column == 4) {
                    return Integer.class; // Qty column
                } else if (column == 5) {
                    return String.class; // Unit Price column
                }
                return super.getColumnClass(column);
            }
        };

        cardTable = new JTable(tableModel);
        cardTable.setFont(cardTable.getFont().deriveFont(14f));
        cardTable.setRowHeight(32);
        cardTable.getColumnModel().getColumn(0).setPreferredWidth(40);  // Checkbox
        cardTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Code
        cardTable.getColumnModel().getColumn(2).setPreferredWidth(280); // Card Name
        cardTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Condition
        cardTable.getColumnModel().getColumn(4).setPreferredWidth(60);  // Qty
        cardTable.getColumnModel().getColumn(5).setPreferredWidth(100); // Unit Price
        cardTable.getColumnModel().getColumn(6).setPreferredWidth(100); // Total

        // Click anywhere on row to select it and check its checkbox
        cardTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = cardTable.rowAtPoint(e.getPoint());
                int column = cardTable.columnAtPoint(e.getPoint());

                if (row >= 0) {
                    // Select the row
                    cardTable.setRowSelectionInterval(row, row);

                    // If NOT clicking the checkbox column itself, toggle checkboxes
                    if (column != 0) {
                        // Deselect all other checkboxes
                        for (int i = 0; i < tableModel.getRowCount(); i++) {
                            if (i != row) {
                                tableModel.setValueAt(false, i, 0);
                            }
                        }
                        // Check this row's checkbox
                        tableModel.setValueAt(true, row, 0);
                    }
                }
            }
        });

        // Enable table sorting but disable auto-sort (maintain chronological order by default)
        javax.swing.table.TableRowSorter<DefaultTableModel> sorter =
                new javax.swing.table.TableRowSorter<>(tableModel);
        cardTable.setRowSorter(sorter);
        // Don't trigger any initial sort - maintains insertion order

        // Custom comparator for the Unit Price column (column 5)
        sorter.setComparator(5, new java.util.Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                try {
                    BigDecimal price1 = new BigDecimal(s1.replace("$", "").replace(",", "").trim());
                    BigDecimal price2 = new BigDecimal(s2.replace("$", "").replace(",", "").trim());
                    return price1.compareTo(price2);
                } catch (Exception e) {
                    return s1.compareTo(s2);
                }
            }
        });

        // Custom comparator for the Total column (column 6)
        sorter.setComparator(6, new java.util.Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                try {
                    BigDecimal price1 = new BigDecimal(s1.replace("$", "").replace(",", "").trim());
                    BigDecimal price2 = new BigDecimal(s2.replace("$", "").replace(",", "").trim());
                    return price1.compareTo(price2);
                } catch (Exception e) {
                    return s1.compareTo(s2);
                }
            }
        });

        // Set up condition dropdown
        JComboBox<String> conditionCombo = new JComboBox<>(CONDITIONS);
        DefaultCellEditor conditionEditor = new DefaultCellEditor(conditionCombo) {
            @Override
            public boolean stopCellEditing() {
                boolean result = super.stopCellEditing();
                if (result) {
                    // Auto-focus search bar after condition change
                    SwingUtilities.invokeLater(() -> cardCodeField.requestFocusInWindow());
                }
                return result;
            }
        };
        cardTable.getColumnModel().getColumn(3).setCellEditor(conditionEditor);

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

        // Set up Qty column editor with validation
        JTextField qtyField = new JTextField();
        qtyField.setHorizontalAlignment(JTextField.CENTER);
        DefaultCellEditor qtyEditor = new DefaultCellEditor(qtyField) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                                                         boolean isSelected, int row, int column) {
                JTextField editor = (JTextField) super.getTableCellEditorComponent(
                        table, value, isSelected, row, column);
                editor.selectAll(); // Select all text when editing
                return editor;
            }

            @Override
            public Object getCellEditorValue() {
                String value = (String) super.getCellEditorValue();
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    return 1;
                }
            }

            @Override
            public boolean stopCellEditing() {
                String value = (String) super.getCellEditorValue();
                try {
                    int qty = Integer.parseInt(value.trim());
                    if (qty <= 0) {
                        JOptionPane.showMessageDialog(getParentWindow(),
                                "Quantity must be a positive integer",
                                "Invalid Quantity",
                                JOptionPane.WARNING_MESSAGE);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(getParentWindow(),
                            "Please enter a valid positive number",
                            "Invalid Quantity",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                boolean result = super.stopCellEditing();
                if (result) {
                    // Auto-focus search bar after successful edit
                    SwingUtilities.invokeLater(() -> cardCodeField.requestFocusInWindow());
                }
                return result;
            }
        };
        cardTable.getColumnModel().getColumn(4).setCellEditor(qtyEditor);

        // Set up Unit Price editor with validation
        JTextField priceField = new JTextField();
        priceField.setHorizontalAlignment(JTextField.RIGHT);
        DefaultCellEditor priceEditor = new DefaultCellEditor(priceField) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                                                         boolean isSelected, int row, int column) {
                JTextField editor = (JTextField) super.getTableCellEditorComponent(
                        table, value, isSelected, row, column);
                // Remove $ sign for editing
                String text = value.toString().replace("$", "").replace(",", "").trim();
                editor.setText(text);
                editor.selectAll();
                return editor;
            }

            @Override
            public Object getCellEditorValue() {
                String value = (String) super.getCellEditorValue();
                value = value.replace("$", "").replace(",", "").trim();

                if (value.isEmpty()) {
                    return "$0.00";
                }

                try {
                    // Check if user entered a decimal point
                    if (value.contains(".")) {
                        // User used decimal: "5.00" → $5.00
                        BigDecimal price = new BigDecimal(value);
                        return String.format("$%.2f", price);
                    } else {
                        // No decimal: interpret as cents: "500" → $5.00
                        int cents = Integer.parseInt(value);
                        BigDecimal price = new BigDecimal(cents).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                        return String.format("$%.2f", price);
                    }
                } catch (NumberFormatException e) {
                    return "$0.00";
                }
            }

            @Override
            public boolean stopCellEditing() {
                String value = (String) super.getCellEditorValue();
                value = value.replace("$", "").replace(",", "").trim();

                if (value.isEmpty()) {
                    JOptionPane.showMessageDialog(getParentWindow(),
                            "Please enter a price",
                            "Invalid Price",
                            JOptionPane.WARNING_MESSAGE);
                    return false;
                }

                try {
                    BigDecimal price;
                    // Parse based on whether decimal point is present
                    if (value.contains(".")) {
                        price = new BigDecimal(value);
                    } else {
                        int cents = Integer.parseInt(value);
                        price = new BigDecimal(cents).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    }

                    if (price.compareTo(BigDecimal.ZERO) < 0) {
                        JOptionPane.showMessageDialog(getParentWindow(),
                                "Price cannot be negative",
                                "Invalid Price",
                                JOptionPane.WARNING_MESSAGE);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(getParentWindow(),
                            "Please enter a valid price",
                            "Invalid Price",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                boolean result = super.stopCellEditing();
                if (result) {
                    // Auto-focus search bar after successful edit
                    SwingUtilities.invokeLater(() -> cardCodeField.requestFocusInWindow());
                }
                return result;
            }
        };
        cardTable.getColumnModel().getColumn(5).setCellEditor(priceEditor);

        // Add table model listener to recalculate on qty or price change
        tableModel.addTableModelListener(e -> {
            int column = e.getColumn();
            if (column == 4) { // Qty column changed
                int row = e.getFirstRow();
                if (row >= 0) {
                    SwingUtilities.invokeLater(() -> {
                        int qty = (Integer) tableModel.getValueAt(row, 4);
                        // keep TradeItem model in sync for exports
                        receivedCards.get(row).setQuantity(qty);
                        updateRowTotal(row);
                        updateSummary();
                    });
                }
            } else if (column == 5) { // Unit Price column changed
                int row = e.getFirstRow();
                if (row >= 0) {
                    SwingUtilities.invokeLater(() -> {
                        updateRowTotal(row);
                        updateSummary();
                    });
                }
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

        JMenuItem scryfallItem = new JMenuItem("Open in Scryfall");
        scryfallItem.addActionListener(e -> {
            int row = cardTable.getSelectedRow();
            if (row >= 0) {
                int modelRow = cardTable.convertRowIndexToModel(row);
                TradeItem item = receivedCards.get(modelRow);
                Card card = item.getCard();

                String scryfallUrl = String.format(
                        "https://scryfall.com/card/%s/%s",
                        card.getSetCode().toLowerCase(),
                        card.getCollectorNumber()
                );

                openInBrowser(scryfallUrl);
            }
        });
        contextMenu.add(scryfallItem);

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

        // Add keyboard shortcuts for table
        InputMap inputMap = cardTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = cardTable.getActionMap();

        // + key to duplicate selected card
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "duplicateCard");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.SHIFT_DOWN_MASK), "duplicateCard");
        actionMap.put("duplicateCard", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                duplicateSelectedCard();
            }
        });

        // F2 key to open search
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "openSearch");
        actionMap.put("openSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSearchDialog();
            }
        });

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

    private JPanel createPartialPaymentPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Split Payment"),
                new EmptyBorder(5, 10, 5, 10)
        ));

        panel.add(new JLabel("Store Credit $"));

        partialCreditField = new JTextField(8);
        partialCreditField.setHorizontalAlignment(JTextField.RIGHT);
        partialCreditField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() != KeyEvent.VK_TAB) {
                    updatePartialCheck();
                }
            }
        });
        panel.add(partialCreditField);

        panel.add(new JLabel("  +  Check $"));

        partialCheckField = new JTextField(8);
        partialCheckField.setHorizontalAlignment(JTextField.RIGHT);
        partialCheckField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() != KeyEvent.VK_TAB) {
                    updatePartialCredit();
                }
            }
        });
        panel.add(partialCheckField);

        JLabel equalsLabel = new JLabel("  =  $0.00");
        equalsLabel.setFont(equalsLabel.getFont().deriveFont(Font.BOLD));
        panel.add(equalsLabel);

        return panel;
    }

    private void updatePartialSplit() {
        // Auto-calculate 50/50 split of current total
        try {
            String totalText = totalPriceLabel.getText();
            // Extract number from "TOTAL PRICE: $123.45 (5 cards)"
            String[] parts = totalText.split("\\$");
            if (parts.length > 1) {
                String numberPart = parts[1].split(" ")[0].replace(",", "");
                BigDecimal total = new BigDecimal(numberPart);
                BigDecimal half = total.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);

                partialCreditField.setText(String.format("%.2f", half));
                partialCheckField.setText(String.format("%.2f", half));
                updatePartialTotal();
            }
        } catch (Exception e) {
            partialCreditField.setText("0.00");
            partialCheckField.setText("0.00");
        }
    }

    private void updatePartialCheck() {
        // User typed in credit payout amount, calculate check payout needed
        // Formula: If customer wants $X credit, they need (X / 0.50) card value for credit
        //          Remaining value = Total - (X / 0.50)
        //          Check payout = Remaining value / 3 (exact 1/3, not 0.33)
        try {
            String totalText = totalPriceLabel.getText();
            String[] parts = totalText.split("\\$");
            if (parts.length > 1) {
                String numberPart = parts[1].split(" ")[0].replace(",", "");
                BigDecimal totalValue = new BigDecimal(numberPart);

                String creditText = partialCreditField.getText().trim();
                if (creditText.isEmpty()) {
                    partialCheckField.setText("");
                    updatePartialTotal();
                    return;
                }

                BigDecimal creditPayout = new BigDecimal(creditText.replace(",", ""));

                // Calculate card value needed for this credit payout (credit is 50% of value)
                BigDecimal valueUsedForCredit = creditPayout.divide(new BigDecimal("0.50"), 2, RoundingMode.HALF_UP);

                // Remaining card value
                BigDecimal remainingValue = totalValue.subtract(valueUsedForCredit);

                if (remainingValue.compareTo(BigDecimal.ZERO) < 0) {
                    remainingValue = BigDecimal.ZERO;
                }

                // Check payout is 1/3 of remaining value (exact division by 3)
                BigDecimal checkPayout = remainingValue.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);

                partialCheckField.setText(String.format("%.2f", checkPayout));
                updatePartialTotal();
            }
        } catch (Exception e) {
            // Invalid number, don't update
        }
    }

    private void updatePartialCredit() {
        // User typed in check payout amount, calculate credit payout for remaining value
        // Formula: If customer wants $X check, they need (X * 3) card value for check (since check = value/3)
        //          Remaining value = Total - (X * 3)
        //          Credit payout = Remaining value * 0.50
        try {
            String totalText = totalPriceLabel.getText();
            String[] parts = totalText.split("\\$");
            if (parts.length > 1) {
                String numberPart = parts[1].split(" ")[0].replace(",", "");
                BigDecimal totalValue = new BigDecimal(numberPart);

                String checkText = partialCheckField.getText().trim();
                if (checkText.isEmpty()) {
                    partialCreditField.setText("");
                    updatePartialTotal();
                    return;
                }

                BigDecimal checkPayout = new BigDecimal(checkText.replace(",", ""));

                // Calculate card value needed for this check payout (check is 1/3 of value, so value = check * 3)
                BigDecimal valueUsedForCheck = checkPayout.multiply(new BigDecimal("3"));

                // Remaining card value
                BigDecimal remainingValue = totalValue.subtract(valueUsedForCheck);

                if (remainingValue.compareTo(BigDecimal.ZERO) < 0) {
                    remainingValue = BigDecimal.ZERO;
                }

                // Credit payout is 50% of remaining value
                BigDecimal creditPayout = remainingValue.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);

                partialCreditField.setText(String.format("%.2f", creditPayout));
                updatePartialTotal();
            }
        } catch (Exception e) {
            // Invalid number, don't update
        }
    }

    private void updatePartialTotal() {
        try {
            String creditText = partialCreditField.getText().trim();
            String checkText = partialCheckField.getText().trim();

            BigDecimal creditPayout = creditText.isEmpty() ? BigDecimal.ZERO : new BigDecimal(creditText.replace(",", ""));
            BigDecimal checkPayout = checkText.isEmpty() ? BigDecimal.ZERO : new BigDecimal(checkText.replace(",", ""));

            // Calculate card value used
            // Credit is 1/2 of value, so value = credit * 2
            BigDecimal valueForCredit = creditPayout.divide(new BigDecimal("0.50"), 2, RoundingMode.HALF_UP);
            // Check is 1/3 of value, so value = check * 3
            BigDecimal valueForCheck = checkPayout.multiply(new BigDecimal("3"));
            BigDecimal totalValueUsed = valueForCredit.add(valueForCheck);

            // Find the equals label in the panel
            Component[] components = partialPaymentPanel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JLabel) {
                    JLabel label = (JLabel) comp;
                    if (label.getText().startsWith("  =  ")) {
                        label.setText(String.format("  =  $%.2f value used", totalValueUsed));

                        // Validate against total card value
                        String totalText = totalPriceLabel.getText();
                        String[] parts = totalText.split("\\$");
                        if (parts.length > 1) {
                            String numberPart = parts[1].split(" ")[0].replace(",", "");
                            BigDecimal totalCardValue = new BigDecimal(numberPart);

                            // Allow small rounding differences (within $0.10)
                            BigDecimal diff = totalValueUsed.subtract(totalCardValue).abs();

                            if (diff.compareTo(new BigDecimal("0.10")) <= 0) {
                                label.setForeground(new Color(0, 150, 0)); // Green if matches
                            } else {
                                label.setForeground(Color.RED); // Red if doesn't match
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
    }

    private void highlightPaymentRate() {
        // Reset both to normal
        halfRateLabel.setFont(halfRateLabel.getFont().deriveFont(Font.PLAIN, 14f));
        thirdRateLabel.setFont(thirdRateLabel.getFont().deriveFont(Font.PLAIN, 14f));
        halfRateLabel.setForeground(UIManager.getColor("Label.foreground"));
        thirdRateLabel.setForeground(UIManager.getColor("Label.foreground"));

        // Highlight the selected rate (none for inventory mode)
        if (storeCreditRadio.isSelected()) {
            halfRateLabel.setFont(halfRateLabel.getFont().deriveFont(Font.BOLD, 16f));
            halfRateLabel.setForeground(new Color(0, 150, 0));
        } else if (checkRadio.isSelected()) {
            thirdRateLabel.setFont(thirdRateLabel.getFont().deriveFont(Font.BOLD, 16f));
            thirdRateLabel.setForeground(new Color(0, 150, 0));
        }
        // No highlight for inventory mode - it's not a payout
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

        // Clear preview if the code changed significantly
        if (lastPreviewCode != null && !lastPreviewCode.equals(parsed.setCode + " " + parsed.collectorNumber)) {
            clearPreview();
        }

        String fetchingCode = parsed.setCode + " " + parsed.collectorNumber;

        new SwingWorker<Card, Void>() {
            @Override
            protected Card doInBackground() throws Exception {
                return apiService.fetchCard(parsed.setCode, parsed.collectorNumber);
            }

            @Override
            protected void done() {
                try {
                    Card card = get();
                    // Only display if the input hasn't changed
                    String currentInput = cardCodeField.getText();
                    ParsedCode currentParsed = parseCardCode(currentInput);
                    if (currentParsed != null) {
                        String currentCode = currentParsed.setCode + " " + currentParsed.collectorNumber;
                        if (currentCode.equals(fetchingCode)) {
                            lastPreviewCode = fetchingCode;
                            displayPreview(card, parsed.finish);
                        }
                    }
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
            JOptionPane.showMessageDialog(getParentWindow(),
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

                    // Check if card has a price
                    boolean isFoil = "F".equals(parsed.finish) || "E".equals(parsed.finish);
                    boolean hasPrice = isFoil ? card.hasFoilPrice() : card.hasNormalPrice();

                    if (!hasPrice) {
                        // No price available - prompt user for manual entry
                        promptForManualPriceOnCard(card, parsed);
                        return;
                    }

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
        String priceInput = JOptionPane.showInputDialog(getParentWindow(),
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
                JOptionPane.showMessageDialog(getParentWindow(),
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
                    1,      // Default qty = 1
                    String.format("$%.2f", price), // Unit price
                    String.format("$%.2f", price)  // Total
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

            // Auto-highlight and scroll to the newly added row
            int lastRow = cardTable.getRowCount() - 1;
            cardTable.setRowSelectionInterval(lastRow, lastRow);
            cardTable.scrollRectToVisible(cardTable.getCellRect(lastRow, 0, true));

            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "Invalid price format. Please enter a valid number.",
                    "Invalid Price",
                    JOptionPane.ERROR_MESSAGE);
            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();
        }
    }

    /**
     * Prompts user for manual price when card exists but has no price
     */
    private void promptForManualPriceOnCard(Card card, ParsedCode parsed) {
        boolean isFoil = "F".equals(parsed.finish) || "E".equals(parsed.finish);
        String finishType = isFoil ? (("E".equals(parsed.finish)) ? "etched" : "foil") : "normal";

        String priceInput = JOptionPane.showInputDialog(getParentWindow(),
                String.format("Card '%s' has no %s price listed.\nEnter manual price:",
                        card.getName(), finishType),
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
                JOptionPane.showMessageDialog(getParentWindow(),
                        "Price must be greater than $0.00",
                        "Invalid Price",
                        JOptionPane.WARNING_MESSAGE);
                cardCodeField.setText("");
                clearPreview();
                cardCodeField.requestFocusInWindow();
                return;
            }

            // Set the price on the card
            if (isFoil) {
                card.setFoilPrice(price.toString());
            } else {
                card.setPrice(price.toString());
            }

            // Now display and add the card
            previewCard = card;
            previewFinish = parsed.finish;
            displayPreview(card, parsed.finish);
            addCard();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(getParentWindow(),
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

        int result = JOptionPane.showConfirmDialog(getParentWindow(), panel,
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
            JOptionPane.showMessageDialog(getParentWindow(),
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
                JOptionPane.showMessageDialog(getParentWindow(),
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
                    1,      // Default qty = 1
                    String.format("$%.2f", price), // Unit price
                    String.format("$%.2f", price)  // Total
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

            // Auto-highlight and scroll to the newly added row
            int lastRow = cardTable.getRowCount() - 1;
            cardTable.setRowSelectionInterval(lastRow, lastRow);
            cardTable.scrollRectToVisible(cardTable.getCellRect(lastRow, 0, true));

            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(getParentWindow(),
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
            price = card.getEtchedPriceAsBigDecimal();
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
        lastPreviewCode = null;
        cardPreviewLabel.setText("Enter a card code above...");
        cardPreviewLabel.setForeground(UIManager.getColor("Label.foreground"));
    }

    private void addCard() {
        if (previewCard == null) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "Please enter a valid card code",
                    "No Card",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean isFoil = "F".equals(previewFinish) || "E".equals(previewFinish);

        // Check if price is available, if not prompt for manual entry
        if ("F".equals(previewFinish) && !previewCard.hasFoilPrice()) {
            // Prompt for manual foil price
            String priceInput = JOptionPane.showInputDialog(getParentWindow(),
                    String.format("Card '%s' has no foil price available.\nEnter manual price:",
                            previewCard.getName()),
                    "Manual Price Entry",
                    JOptionPane.QUESTION_MESSAGE);

            if (priceInput == null || priceInput.trim().isEmpty()) {
                return; // User cancelled
            }

            try {
                priceInput = priceInput.replace("$", "").replace(",", "").trim();
                BigDecimal price = new BigDecimal(priceInput);

                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(getParentWindow(),
                            "Price must be greater than $0.00",
                            "Invalid Price",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // Set the foil price
                previewCard.setFoilPrice(price.toString());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(getParentWindow(),
                        "Invalid price format. Please enter a valid number.",
                        "Invalid Price",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else if ("E".equals(previewFinish) && !previewCard.hasEtchedPrice()) {
            // Prompt for manual etched price
            String priceInput = JOptionPane.showInputDialog(getParentWindow(),
                    String.format("Card '%s' has no etched price available.\nEnter manual price:",
                            previewCard.getName()),
                    "Manual Price Entry",
                    JOptionPane.QUESTION_MESSAGE);

            if (priceInput == null || priceInput.trim().isEmpty()) {
                return; // User cancelled
            }

            try {
                priceInput = priceInput.replace("$", "").replace(",", "").trim();
                BigDecimal price = new BigDecimal(priceInput);

                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(getParentWindow(),
                            "Price must be greater than $0.00",
                            "Invalid Price",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // Set the etched price
                previewCard.setEtchedPrice(price.toString());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(getParentWindow(),
                        "Invalid price format. Please enter a valid number.",
                        "Invalid Price",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        if (!isFoil && !previewCard.hasNormalPrice()) {
            // Prompt for manual normal price
            String priceInput = JOptionPane.showInputDialog(getParentWindow(),
                    String.format("Card '%s' has no normal price available.\nEnter manual price:",
                            previewCard.getName()),
                    "Manual Price Entry",
                    JOptionPane.QUESTION_MESSAGE);

            if (priceInput == null || priceInput.trim().isEmpty()) {
                return; // User cancelled
            }

            try {
                priceInput = priceInput.replace("$", "").replace(",", "").trim();
                BigDecimal price = new BigDecimal(priceInput);

                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(getParentWindow(),
                            "Price must be greater than $0.00",
                            "Invalid Price",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // Set the normal price
                previewCard.setPrice(price.toString());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(getParentWindow(),
                        "Invalid price format. Please enter a valid number.",
                        "Invalid Price",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        TradeItem item = new TradeItem(previewCard, isFoil, 1, previewFinish);
        receivedCards.add(item);
        cardConditions.add("NM"); // Default to NM condition

        Card card = item.getCard();
        String code = card.getSetCode() + " " + card.getCollectorNumber();

        if (isFoil) {
            code += "E".equals(previewFinish) ? "e" : "f"; // Lowercase, no space
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
                1,    // Default qty = 1
                String.format("$%.2f", roundedPrice), // Unit price
                String.format("$%.2f", roundedPrice)  // Total (qty * unit price)
        });

        updateSummary();

        // Auto-highlight and scroll to the newly added row
        int lastRow = cardTable.getRowCount() - 1;
        cardTable.setRowSelectionInterval(lastRow, lastRow);
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
     * Duplicates the selected card (adds another copy with same attributes)
     */
    private void duplicateSelectedCard() {
        int row = cardTable.getSelectedRow();
        if (row < 0) {
            return; // No selection
        }

        // Convert view row to model row since table might be sorted
        int modelRow = cardTable.convertRowIndexToModel(row);

        // Get existing card data
        TradeItem existingItem = receivedCards.get(modelRow);
        String condition = (String) tableModel.getValueAt(modelRow, 3);
        Object qtyObj = tableModel.getValueAt(modelRow, 4);
        int qty = (qtyObj instanceof Integer) ? (Integer) qtyObj : Integer.parseInt(qtyObj.toString());
        String unitPrice = (String) tableModel.getValueAt(modelRow, 5);
        String code = (String) tableModel.getValueAt(modelRow, 1);
        String name = (String) tableModel.getValueAt(modelRow, 2);

        // Calculate total
        BigDecimal price = new BigDecimal(unitPrice.replace("$", "").replace(",", "").trim());
        BigDecimal total = price.multiply(BigDecimal.valueOf(qty));

        // Create new TradeItem copy
        TradeItem newItem = new TradeItem(existingItem.getCard(), existingItem.isFoil());
        newItem.setQuantity(qty);
        newItem.setUnitPrice(price);

        // Add to lists
        receivedCards.add(newItem);
        cardConditions.add(condition);

        // Add to table
        tableModel.addRow(new Object[]{
                false,  // Checkbox
                code,
                name,
                condition,
                qty,
                unitPrice,
                String.format("$%.2f", total)
        });

        updateSummary();

        // Select the newly added duplicate
        int lastRow = cardTable.getRowCount() - 1;
        cardTable.setRowSelectionInterval(lastRow, lastRow);
        cardTable.scrollRectToVisible(cardTable.getCellRect(lastRow, 0, true));
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
            JOptionPane.showMessageDialog(getParentWindow(),
                    "No cards selected for deletion",
                    "Nothing to Delete",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(getParentWindow(),
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

        // Update the unit price in the table (Column 5 is Unit Price)
        tableModel.setValueAt(String.format("$%.2f", conditionPrice), modelRow, 5);

        // Recalculate and update total
        updateRowTotal(modelRow);
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
        // Always recalculate - the cache optimization was causing issues
        BigDecimal total = BigDecimal.ZERO;
        int totalQty = 0;

        int rowCount = tableModel.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            // Get total directly from table (Column 6 is Total)
            String totalStr = (String) tableModel.getValueAt(i, 6);
            totalStr = totalStr.replace("$", "").replace(",", "").trim();

            try {
                BigDecimal rowTotal = new BigDecimal(totalStr);
                total = total.add(rowTotal);

                // Get qty from table (Column 4 is Qty)
                Object qtyObj = tableModel.getValueAt(i, 4);
                int qty = (qtyObj instanceof Integer) ? (Integer) qtyObj : Integer.parseInt(qtyObj.toString());
                totalQty += qty;
            } catch (Exception e) {
                // If parsing fails, calculate from unit price and qty
                try {
                    String unitPriceStr = (String) tableModel.getValueAt(i, 5);
                    Object qtyObj = tableModel.getValueAt(i, 4);
                    int qty = (qtyObj instanceof Integer) ? (Integer) qtyObj : Integer.parseInt(qtyObj.toString());
                    BigDecimal unitPrice = new BigDecimal(unitPriceStr.replace("$", "").replace(",", "").trim());
                    total = total.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
                    totalQty += qty;
                } catch (Exception ex) {
                    // Skip this row if all parsing fails
                }
            }
        }

        BigDecimal halfRate = total.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal thirdRate = total.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);

        totalPriceLabel.setText(String.format("TOTAL: $%.2f (%d cards)", total, totalQty));
        halfRateLabel.setText(String.format("HALF RATE (50%%): $%.2f", halfRate));
        thirdRateLabel.setText(String.format("THIRD RATE (33.33%%): $%.2f", thirdRate));

        // Update partial payment if it's visible
        if (partialRadio.isSelected() && partialPaymentPanel.isVisible()) {
            updatePartialSplit();
        }
    }

    private void clearAll() {
        if (receivedCards.isEmpty()) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(getParentWindow(),
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
            JOptionPane.showMessageDialog(getParentWindow(),
                    "No cards to export",
                    "Empty List",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String traderName = traderNameField.getText().trim();
        if (traderName.isEmpty()) {
            traderName = "Unknown";
        }

        // Filter out MISC cards and extract corresponding table values
        List<TradeItem> nonMiscCards = new ArrayList<>();
        List<BigDecimal> nonMiscUnitPrices = new ArrayList<>();
        List<Integer> nonMiscQuantities = new ArrayList<>();
        int miscCount = 0;

        // Iterate through table rows (not receivedCards, in case table is sorted)
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            // Get card code to identify if it's MISC
            String code = (String) tableModel.getValueAt(i, 1); // Column 1 is Code

            if (!code.startsWith("MISC")) {
                // This is a real card, not MISC
                TradeItem item = receivedCards.get(i);
                nonMiscCards.add(item);

                // Extract unit price from table (Column 5)
                String unitPriceStr = (String) tableModel.getValueAt(i, 5);
                BigDecimal unitPrice = new BigDecimal(unitPriceStr.replace("$", "").replace(",", "").trim());
                nonMiscUnitPrices.add(unitPrice);

                // Extract quantity from table (Column 4)
                Object qtyObj = tableModel.getValueAt(i, 4);
                int qty = (qtyObj instanceof Integer) ? (Integer) qtyObj : Integer.parseInt(qtyObj.toString());
                nonMiscQuantities.add(qty);
            } else {
                miscCount++;
            }
        }

        if (nonMiscCards.isEmpty()) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "No cards to export - all cards are MISC cards.\n" +
                            "MISC cards are excluded from POS import.",
                    "Nothing to Export",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Determine payment type
            String paymentType;
            String paymentDisplay;

            if (inventoryRadio.isSelected()) {
                paymentType = "inventory";
                paymentDisplay = "Inventory (0%)";
            } else if (checkRadio.isSelected()) {
                paymentType = "check";
                paymentDisplay = "Check (33.33%)";
            } else if (partialRadio.isSelected()) {
                // Validate partial payment - check that card value is fully used
                try {
                    String creditText = partialCreditField.getText().trim();
                    String checkText = partialCheckField.getText().trim();

                    BigDecimal creditPayout = creditText.isEmpty() ? BigDecimal.ZERO : new BigDecimal(creditText);
                    BigDecimal checkPayout = checkText.isEmpty() ? BigDecimal.ZERO : new BigDecimal(checkText);

                    // Calculate card value used
                    // Credit payout uses: creditPayout / 0.50 of card value (credit = value * 0.5, so value = credit / 0.5)
                    // Check payout uses: checkPayout * 3 of card value (check = value / 3, so value = check * 3)
                    BigDecimal valueForCredit = creditPayout.divide(new BigDecimal("0.50"), 2, RoundingMode.HALF_UP);
                    BigDecimal valueForCheck = checkPayout.multiply(new BigDecimal("3"));
                    BigDecimal totalValueUsed = valueForCredit.add(valueForCheck);

                    BigDecimal totalCardValue = calculateTotalValue(nonMiscCards);

                    // Allow small rounding differences (within $0.10)
                    BigDecimal diff = totalValueUsed.subtract(totalCardValue).abs();
                    if (diff.compareTo(new BigDecimal("0.10")) > 0) {
                        JOptionPane.showMessageDialog(getParentWindow(),
                                String.format("Partial payment doesn't match trade value!\n\n" +
                                                "Card Value: $%.2f\n" +
                                                "Credit Payout: $%.2f (uses $%.2f value @ 50%%)\n" +
                                                "Check Payout: $%.2f (uses $%.2f value @ 33%%)\n" +
                                                "Total Value Used: $%.2f\n\n" +
                                                "Difference: $%.2f\n\n" +
                                                "Please adjust the amounts.",
                                        totalCardValue, creditPayout, valueForCredit,
                                        checkPayout, valueForCheck, totalValueUsed, diff),
                                "Invalid Partial Payment",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    paymentType = "partial";
                    paymentDisplay = String.format("Partial (Credit: $%.2f + Check: $%.2f)", creditPayout, checkPayout);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(getParentWindow(),
                            "Invalid partial payment amounts. Please enter valid numbers.",
                            "Invalid Input",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                paymentType = "credit"; // Store credit
                paymentDisplay = "Store Credit (50%)";
            }

            // Use new method with table values and payment type
            String filename = exportService.exportToPOSFormat(
                    nonMiscCards, traderName, nonMiscUnitPrices, nonMiscQuantities, paymentType);

            String message = String.format("POS import file created!\n\n" +
                            "File: %s\n" +
                            "Cards Exported: %d\n" +
                            "Total Value: $%.2f\n" +
                            "Payment Type: %s\n\n" +
                            "Ready to import into your POS system.",
                    filename,
                    nonMiscCards.size(),
                    calculateTotalValue(nonMiscCards),
                    paymentDisplay);

            if (miscCount > 0) {
                message += String.format("\n\nNote: %d MISC card(s) were excluded from export.", miscCount);
            }

            JOptionPane.showMessageDialog(getParentWindow(),
                    message,
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "Export failed: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void saveList() {
        if (receivedCards.isEmpty()) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "No cards to save",
                    "Empty List",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check if this is inventory mode
        if (inventoryRadio.isSelected()) {
            // Inventory mode - export to inventory format instead
            addToInventory();
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

        // Extract table values
        List<BigDecimal> unitPrices = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            // Extract unit price from table (Column 5)
            String unitPriceStr = (String) tableModel.getValueAt(i, 5);
            BigDecimal unitPrice = new BigDecimal(unitPriceStr.replace("$", "").replace(",", "").trim());
            unitPrices.add(unitPrice);

            // Extract quantity from table (Column 4) - handle both Integer and String
            Object qtyObj = tableModel.getValueAt(i, 4);
            int qty = (qtyObj instanceof Integer) ? (Integer) qtyObj : Integer.parseInt(qtyObj.toString());
            quantities.add(qty);
        }

        try {
            // Use new method with table values
            String filename = exportService.saveCardList(
                    receivedCards,
                    traderName,
                    customerName,
                    driversLicense,
                    checkNumber,
                    isStoreCredit,
                    cardConditions,
                    unitPrices,
                    quantities
            );

            JOptionPane.showMessageDialog(getParentWindow(),
                    String.format("Card list saved!\n\nFile: %s", filename),
                    "Saved",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(getParentWindow(),
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
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            TradeItem receivedItem = receivedCards.get(i);
            if (!items.contains(receivedItem)) continue;
            // Column 6 is Total (String like "$12.00")
            Object totalObj = tableModel.getValueAt(i, 6);
            if (totalObj == null) continue;
            String totalStr = totalObj.toString().replace("$", "").replace(",", "").trim();
            try {
                total = total.add(new BigDecimal(totalStr));
            } catch (Exception ignored) {
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

        // Check for finish indicators - handle both "1f" and "1 f" formats
        String finish = "";

        // Check if ends with 'f' or 'e' (case insensitive, already uppercased)
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

        // Collector number may have letter suffixes (e.g., "143b", "5a")
        StringBuilder collectorNumber = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            collectorNumber.append(parts[i]);
        }

        // Handle case where user types without space (e.g., "CHR143B")
        if (parts.length == 1 && parts[0].length() > 3) {
            String combined = parts[0];
            if (combined.length() >= 4) {
                setCode = combined.substring(0, 3);
                collectorNumber = new StringBuilder(combined.substring(3));
            }
        }

        // Apply set conversion mapping (e.g., CHK -> chk for Scryfall API)
        String apiSetCode = SetList.toScryfallCode(setCode);

        // Scryfall expects lowercase collector numbers with letter suffixes
        String apiCollectorNumber = collectorNumber.toString().toLowerCase();

        return new ParsedCode(apiSetCode, apiCollectorNumber, finish);
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
            formatted.append(parsed.finish.toLowerCase()); // Lowercase, no space
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

    /**
     * Exports trade items directly to inventory (Item Wizard Change Qty format)
     */
    private void addToInventory() {
        if (receivedCards.isEmpty()) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "No cards in trade to add to inventory",
                    "Empty Trade",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(getParentWindow(),
                String.format("Add %d card(s) to inventory?\n\nThis will export in Item Wizard Change Qty format.",
                        receivedCards.size()),
                "Confirm Add to Inventory",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // Get quantities from table
                List<Integer> quantities = new ArrayList<>();
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    quantities.add((Integer) tableModel.getValueAt(i, 4));
                }

                String filename = exportService.exportToInventoryFormat(
                        receivedCards, cardConditions, quantities);

                JOptionPane.showMessageDialog(getParentWindow(),
                        String.format("Cards added to inventory!\n\nFile: %s\n\nMISC cards were excluded from export.",
                                filename),
                        "Inventory Updated",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(getParentWindow(),
                        "Failed to add to inventory: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    /**
     * Updates the total for a specific row based on quantity and unit price
     */
    private void updateRowTotal(int row) {
        if (row < 0 || row >= tableModel.getRowCount()) {
            return;
        }

        int qty = (Integer) tableModel.getValueAt(row, 4);
        String unitPriceStr = (String) tableModel.getValueAt(row, 5);
        BigDecimal unitPrice = new BigDecimal(unitPriceStr.replace("$", "").trim());
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(qty));
        tableModel.setValueAt(String.format("$%.2f", total), row, 6);
    }

    /**
     * Opens a URL in the system's default browser
     */
    private void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                // Fallback for systems without Desktop support
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec("open " + url);
                } else if (os.contains("nix") || os.contains("nux")) {
                    Runtime.getRuntime().exec("xdg-open " + url);
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "Could not open browser.\n\nURL: " + url + "\n\nPlease copy and paste this URL into your browser.",
                    "Browser Error",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Gets the parent window for centering dialogs
     */
    private Window getParentWindow() {
        return SwingUtilities.getWindowAncestor(this);
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