package com.cardpricer.gui.panel;

import com.cardpricer.gui.CardImagePopup;
import com.cardpricer.gui.dialog.CardSearchDialog;
import com.cardpricer.gui.panel.trade.PaymentTypePanel;
import com.cardpricer.gui.panel.trade.TradeSummaryPanel;
import com.cardpricer.model.Card;
import com.cardpricer.model.ParsedCode;
import com.cardpricer.model.TradeItem;
import com.cardpricer.service.PricingService;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.service.TradeReceivingExportService;
import com.cardpricer.util.CardCodeParser;
import com.cardpricer.util.CardConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
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
    private final PricingService pricingService = new PricingService();

    private List<TradeItem> receivedCards;
    private List<String> cardConditions; // Track condition for each card

    // Input field
    private JTextField cardCodeField;
    private JLabel cardPreviewLabel;

    // Table
    private JTable cardTable;
    private DefaultTableModel tableModel;

    // Summary and payment sub-panels
    private final TradeSummaryPanel summaryPanel = new TradeSummaryPanel();
    private PaymentTypePanel paymentTypePanel;

    // Trade info fields
    private JTextField traderNameField;
    private JTextField customerNameField;
    private JTextField driversLicenseField;
    private JTextField checkNumberField;

    // Preview card
    private Card previewCard;
    private String lastPreviewCode; // Track what code the preview is showing
    private String previewFinish;
    /** Stores the raw set code from the user's input (e.g. "plst") to distinguish
     *  PLST entries so the table can show "PLST ARB 1" while saving "ARB 1". */
    private String previewOriginalSetCode = "";

    /** Lazy-initialized hover image popup. */
    private CardImagePopup imagePopup;

    /**
     * Comparator for price columns that parses {@code "$1.23"}-style strings into
     * {@link BigDecimal} values for numeric ordering. Falls back to lexicographic
     * comparison if parsing fails.
     */
    private static final java.util.Comparator<String> PRICE_COMPARATOR = (s1, s2) -> {
        try {
            BigDecimal p1 = new BigDecimal(s1.replace("$", "").replace(",", "").trim());
            BigDecimal p2 = new BigDecimal(s2.replace("$", "").replace(",", "").trim());
            return p1.compareTo(p2);
        } catch (Exception e) {
            return s1.compareTo(s2);
        }
    };

    public TradePanel() {
        this.apiService = new ScryfallApiService();
        this.exportService = new TradeReceivingExportService();
        this.receivedCards = new ArrayList<>();
        this.cardConditions = new ArrayList<>();

        // paymentTypePanel must be initialised before createInputPanel() is called
        paymentTypePanel = new PaymentTypePanel(this::onPaymentSelectionChanged);

        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(createInputPanel(), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        // Numpad + duplicates the most recently selected row from anywhere in the panel
        InputMap panelIM = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap panelAM = getActionMap();
        panelIM.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "globalDuplicate");
        panelAM.put("globalDuplicate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                duplicateSelectedCard();
            }
        });

        SwingUtilities.invokeLater(() -> cardCodeField.requestFocusInWindow());
    }

    // -------------------------------------------------------------------------
    // Payment selection callback
    // -------------------------------------------------------------------------

    /** Called by PaymentTypePanel whenever the radio selection changes. */
    private void onPaymentSelectionChanged() {
        boolean isCheck = paymentTypePanel.isCheckSelected();
        checkNumberField.setEnabled(isCheck);
        if (!isCheck) {
            checkNumberField.setText("");
        } else {
            checkNumberField.requestFocusInWindow();
        }
        refreshSummary();
    }

    // -------------------------------------------------------------------------
    // Panel construction
    // -------------------------------------------------------------------------

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
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        tradeInfoPanel.add(new JLabel("Trader Name:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        traderNameField = new JTextField(15);
        tradeInfoPanel.add(traderNameField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        tradeInfoPanel.add(new JLabel("Customer Name:"), gbc);

        gbc.gridx = 3; gbc.weightx = 1.0;
        customerNameField = new JTextField(15);
        tradeInfoPanel.add(customerNameField, gbc);

        // Row 2: Driver's License and Check Number
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        tradeInfoPanel.add(new JLabel("Driver's License:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        driversLicenseField = new JTextField(15);
        tradeInfoPanel.add(driversLicenseField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        tradeInfoPanel.add(new JLabel("Check Number:"), gbc);

        gbc.gridx = 3; gbc.weightx = 1.0;
        checkNumberField = new JTextField(15);
        checkNumberField.setEnabled(false); // Disabled by default
        tradeInfoPanel.add(checkNumberField, gbc);

        // Row 3: Payment Method — embed PaymentTypePanel
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        tradeInfoPanel.add(new JLabel("Payment Method:"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        tradeInfoPanel.add(paymentTypePanel, gbc);

        panel.add(tradeInfoPanel, BorderLayout.NORTH);

        // Middle: Input field
        JPanel inputPanel = new JPanel(new BorderLayout(10, 5));

        JLabel instructionLabel = new JLabel("<html>Enter code + ENTER &nbsp;|&nbsp; <b>TDM 3</b> (normal) &nbsp;<b>3f</b> (foil) &nbsp;<b>3e</b> (etched) &nbsp;<b>3s</b> (surge foil) &nbsp;|&nbsp; <b>PLST ARB 1</b> (List card) &nbsp;|&nbsp; <b>misc</b> = manual entry &nbsp;|&nbsp; <b>Ctrl+F</b> / <b>F2</b> = search by name</html>");
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
                    ParsedCode parsed = CardCodeParser.parse(input);
                    if (parsed != null) {
                        String formatted = CardCodeParser.format(parsed);
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

        // Card image hover popup — show card art while hovering over a table row
        cardTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = cardTable.rowAtPoint(e.getPoint());
                if (row < 0) { getImagePopup().hide(); return; }
                int modelRow = cardTable.convertRowIndexToModel(row);
                if (modelRow < 0 || modelRow >= receivedCards.size()) { getImagePopup().hide(); return; }
                String url = receivedCards.get(modelRow).getCard().getImageUrl();
                getImagePopup().show(url, e.getLocationOnScreen());
            }
        });
        cardTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) { getImagePopup().hide(); }
        });

        // Enable table sorting but disable auto-sort (maintain chronological order by default)
        javax.swing.table.TableRowSorter<DefaultTableModel> sorter =
                new javax.swing.table.TableRowSorter<>(tableModel);
        cardTable.setRowSorter(sorter);
        // Don't trigger any initial sort - maintains insertion order

        // Numeric price comparator for the Unit Price column (column 5) and Total column (column 6)
        sorter.setComparator(5, PRICE_COMPARATOR);
        sorter.setComparator(6, PRICE_COMPARATOR);

        // Set up condition dropdown
        JComboBox<String> conditionCombo = new JComboBox<>(CardConstants.CONDITIONS);
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
                        refreshSummary();
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
                        refreshSummary();
                    });
                }
            } else if (column == 5) { // Unit Price column changed
                int row = e.getFirstRow();
                if (row >= 0) {
                    SwingUtilities.invokeLater(() -> {
                        updateRowTotal(row);
                        refreshSummary();
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
                    refreshSummary();
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

        // Right: Summary (owned by TradeSummaryPanel)
        panel.add(summaryPanel, BorderLayout.EAST);

        // Highlight the initial payment rate
        summaryPanel.update(BigDecimal.ZERO, 0, paymentTypePanel.getPaymentType());

        return panel;
    }

    // -------------------------------------------------------------------------
    // Preview
    // -------------------------------------------------------------------------

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

        ParsedCode parsed = CardCodeParser.parse(input);
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
                    ParsedCode currentParsed = CardCodeParser.parse(currentInput);
                    if (currentParsed != null) {
                        String currentCode = currentParsed.setCode + " " + currentParsed.collectorNumber;
                        if (currentCode.equals(fetchingCode)) {
                            lastPreviewCode = fetchingCode;
                            previewOriginalSetCode = parsed.setCode;
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

        ParsedCode parsed = CardCodeParser.parse(input);
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
                    previewOriginalSetCode = parsed.setCode;

                    // Check if card has a price
                    boolean isFoil = "F".equals(parsed.finish) || "E".equals(parsed.finish) || "S".equals(parsed.finish);
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

            refreshSummary();

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
        boolean isFoil = "F".equals(parsed.finish) || "E".equals(parsed.finish) || "S".equals(parsed.finish);
        String finishType = "E".equals(parsed.finish) ? "etched"
                : "S".equals(parsed.finish) ? "surge foil"
                : isFoil ? "foil" : "normal";

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

        // Auto-focus the name field when the dialog becomes visible
        nameField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && nameField.isShowing()) {
                SwingUtilities.invokeLater(nameField::requestFocusInWindow);
            }
        });

        // Enter in name field moves focus to price field (does not submit yet)
        nameField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    priceField.requestFocusInWindow();
                    e.consume();
                }
            }
        });

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Card Name:"), gbc);

        gbc.gridx = 1;
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
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

            refreshSummary();

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
        } else if ("S".equals(finish)) {
            price = card.getFoilPriceAsBigDecimal();
            finishName = "Surge Foil";
        } else {
            price = card.getPriceAsBigDecimal();
            finishName = "Normal";
        }

        BigDecimal roundedPrice = pricingService.applyPricingRules(price, card.getRarity());

        text.append(String.format(" (%s) - $%.2f [%s %s]",
                finishName, roundedPrice, card.getSetCode(), CardCodeParser.capitalize(card.getRarity())));

        cardPreviewLabel.setText(text.toString());
        cardPreviewLabel.setForeground(price.compareTo(BigDecimal.ZERO) > 0 ?
                new Color(0, 120, 0) : Color.RED);
    }

    private void clearPreview() {
        previewCard = null;
        previewFinish = null;
        lastPreviewCode = null;
        previewOriginalSetCode = "";
        cardPreviewLabel.setText("Enter a card code above...");
        cardPreviewLabel.setForeground(UIManager.getColor("Label.foreground"));
    }

    // -------------------------------------------------------------------------
    // Card management
    // -------------------------------------------------------------------------

    private void addCard() {
        if (previewCard == null) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "Please enter a valid card code",
                    "No Card",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean isFoil = "F".equals(previewFinish) || "E".equals(previewFinish) || "S".equals(previewFinish);

        // Check if price is available, if not prompt for manual entry
        if (("F".equals(previewFinish) || "S".equals(previewFinish)) && !previewCard.hasFoilPrice()) {
            // Prompt for manual foil/surge-foil price
            String finishLabel = "S".equals(previewFinish) ? "surge foil" : "foil";
            String priceInput = JOptionPane.showInputDialog(getParentWindow(),
                    String.format("Card '%s' has no %s price available.\nEnter manual price:",
                            previewCard.getName(), finishLabel),
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
        // For PLST cards, display "PLST ARB 1" in the table; saves use the underlying "ARB 1"
        String baseCode = "plst".equalsIgnoreCase(previewOriginalSetCode)
                ? "PLST " + card.getSetCode() + " " + card.getCollectorNumber()
                : card.getSetCode() + " " + card.getCollectorNumber();

        String code = baseCode;
        if (isFoil) {
            if ("E".equals(previewFinish)) {
                code += "e";
            } else if ("S".equals(previewFinish)) {
                code += "s";
            } else {
                code += "f";
            }
        }

        StringBuilder name = new StringBuilder(card.getName());
        if (card.getFrameEffectDisplay() != null) {
            name.append(" - ").append(card.getFrameEffectDisplay());
        }
        if (isFoil) {
            String finishLabel = "E".equals(previewFinish) ? "Etched"
                    : "S".equals(previewFinish) ? "Surge Foil" : "Foil";
            name.append(" (").append(finishLabel).append(")");
        }

        BigDecimal roundedPrice = pricingService.applyPricingRules(item.getUnitPrice(), card.getRarity());

        tableModel.addRow(new Object[]{
                false,  // Checkbox unchecked by default
                code,
                name.toString(),
                "NM", // Default condition
                1,    // Default qty = 1
                String.format("$%.2f", roundedPrice), // Unit price
                String.format("$%.2f", roundedPrice)  // Total (qty * unit price)
        });

        refreshSummary();

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
            refreshSummary();
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

        refreshSummary();

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
            refreshSummary();
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
        BigDecimal basePrice = pricingService.applyPricingRules(item.getUnitPrice(), card.getRarity());

        // Apply condition multiplier
        BigDecimal conditionPrice = pricingService.applyConditionMultiplier(basePrice, condition);

        // Update the unit price in the table (Column 5 is Unit Price)
        tableModel.setValueAt(String.format("$%.2f", conditionPrice), modelRow, 5);

        // Recalculate and update total
        updateRowTotal(modelRow);
    }

    // -------------------------------------------------------------------------
    // Summary
    // -------------------------------------------------------------------------

    /** Recomputes totals from the table and pushes updates to sub-panels. */
    private void refreshSummary() {
        BigDecimal total = BigDecimal.ZERO;
        int totalQty = 0;

        int rowCount = tableModel.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            String totalStr = (String) tableModel.getValueAt(i, 6);
            totalStr = totalStr.replace("$", "").replace(",", "").trim();

            try {
                BigDecimal rowTotal = new BigDecimal(totalStr);
                total = total.add(rowTotal);

                Object qtyObj = tableModel.getValueAt(i, 4);
                int qty = (qtyObj instanceof Integer) ? (Integer) qtyObj : Integer.parseInt(qtyObj.toString());
                totalQty += qty;
            } catch (Exception e) {
                // Fallback: calculate from unit price and qty
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

        // Update PaymentTypePanel so partial split fields auto-update if visible
        paymentTypePanel.setTotal(total);

        // Update summary labels
        summaryPanel.update(total, totalQty, paymentTypePanel.getPaymentType());
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

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
            refreshSummary();
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
            String currentPayment = paymentTypePanel.getPaymentType();

            if ("inventory".equals(currentPayment)) {
                paymentType = "inventory";
                paymentDisplay = "Inventory (0%)";
            } else if ("check".equals(currentPayment)) {
                paymentType = "check";
                paymentDisplay = "Check (33.33%)";
            } else if ("partial".equals(currentPayment)) {
                // Validate partial payment - check that card value is fully used
                try {
                    BigDecimal creditPayout = paymentTypePanel.getPartialCreditPayout();
                    BigDecimal checkPayout  = paymentTypePanel.getPartialCheckPayout();

                    // Calculate card value used
                    BigDecimal valueForCredit = creditPayout.divide(new BigDecimal("0.50"), 2, RoundingMode.HALF_UP);
                    BigDecimal valueForCheck  = checkPayout.multiply(new BigDecimal("3"));
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
        if ("inventory".equals(paymentTypePanel.getPaymentType())) {
            // Inventory mode - export to inventory format instead
            addToInventory();
            return;
        }

        String traderName = traderNameField.getText().trim();
        String customerName = customerNameField.getText().trim();
        String driversLicense = driversLicenseField.getText().trim();
        String checkNumber = checkNumberField.getText().trim();
        String paymentType = paymentTypePanel.getPaymentType();

        BigDecimal partialCredit = BigDecimal.ZERO;
        BigDecimal partialCheck = BigDecimal.ZERO;
        if ("partial".equals(paymentType)) {
            try {
                partialCredit = paymentTypePanel.getPartialCreditPayout();
                partialCheck  = paymentTypePanel.getPartialCheckPayout();
            } catch (NumberFormatException ignored) {
                // Use zeros if fields are invalid
            }
        }

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
                    paymentType,
                    partialCredit,
                    partialCheck,
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

    private CardImagePopup getImagePopup() {
        if (imagePopup == null) {
            imagePopup = new CardImagePopup(SwingUtilities.getWindowAncestor(this));
        }
        return imagePopup;
    }
}
