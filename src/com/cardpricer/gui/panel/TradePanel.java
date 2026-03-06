package com.cardpricer.gui.panel;

import com.cardpricer.gui.CardImagePopup;
import com.cardpricer.gui.ShortcutHelpDialog;
import com.cardpricer.gui.dialog.CardSearchDialog;
import com.cardpricer.gui.panel.trade.PaymentTypePanel;
import com.cardpricer.gui.panel.trade.TradeSummaryPanel;
import com.cardpricer.model.Card;
import com.cardpricer.model.ParsedCode;
import com.cardpricer.model.TradeItem;
import com.cardpricer.service.BuyRateService;
import com.cardpricer.service.PricingService;
import com.cardpricer.service.ReceiptPrintService;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.service.TradeReceivingExportService;
import com.cardpricer.service.TradeSessionService;
import com.cardpricer.util.CardCodeParser;
import com.cardpricer.util.CardConstants;
import com.cardpricer.util.VintageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
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
import javax.imageio.ImageIO;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Quick-entry panel for receiving cards from trades and purchases.
 *
 * <p>Cards are entered via a code field using the formats:
 * <ul>
 *   <li>{@code TDM 3} — normal finish</li>
 *   <li>{@code TDM 3f} — foil finish</li>
 *   <li>{@code TDM 3e} — etched finish</li>
 *   <li>{@code TDM 3s} — surge foil finish</li>
 *   <li>{@code PLST ARB 1} — The List reprint</li>
 *   <li>{@code misc} — manual entry with custom name and price</li>
 * </ul>
 *
 * <p>Prices are fetched from the Scryfall API, rounded via {@link com.cardpricer.service.PricingService},
 * and can be exported to a POS CSV file or saved as a plain-text trade receipt.
 * Keyboard shortcuts: {@code Ctrl+F}/{@code F2} = search by name, {@code Numpad +} = duplicate row,
 * {@code Ctrl+Z} = undo last add, {@code F1} = shortcut help.
 */
public class TradePanel extends JPanel {

    // Help dialog content
    private static final String   HELP_TITLE = "Trade Panel — Help";
    private static final String[] HELP_COLS  = {"Shortcut / Code", "Description"};
    private static final String[][] HELP_ROWS = {
        {"--- Keyboard Shortcuts", ""},
        {"Enter",          "Add card from the code field"},
        {"+ / Numpad +",   "Duplicate the selected row"},
        {"Ctrl+Z",         "Undo the last added card"},
        {"Ctrl+F / F2",    "Search for a card by name"},
        {"/",              "Jump to card code field"},
        {"F4 / [Vintage]", "Show vintage set code reference"},
        {"F1 / [?]",       "Show this help dialog"},
        {"--- Code Formats", ""},
        {"TDM 3",          "Normal finish (set code + number)"},
        {"TDM 3f",         "Foil finish"},
        {"TDM 3e",         "Etched finish"},
        {"TDM 3s",         "Surge foil"},
        {"PLST ARB 1",     "The List card (PLST + original set + number)"},
        {"misc",           "Manual entry — prompts for name & price"},
    };

    private final ScryfallApiService apiService;
    private final TradeReceivingExportService exportService;
    private final PricingService pricingService = new PricingService();
    private final BuyRateService buyRateService = new BuyRateService();
    private int lastKnownBuyRateGen = BuyRateService.getSaveGeneration();

    /** Last tiered credit total computed by refreshSummary(); used by saveList(). */
    private BigDecimal lastTierCreditTotal = BigDecimal.ZERO;
    /** Last tiered check total computed by refreshSummary(); used by saveList(). */
    private BigDecimal lastTierCheckTotal  = BigDecimal.ZERO;

    private List<TradeItem> receivedCards;
    private List<String> cardConditions; // Track condition for each card
    private final List<BuyRateService.PayoutResult> rowPayouts = new ArrayList<>();
    private boolean isRefreshingSummary = false;

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

    // ── Feature: Print / PDF ─────────────────────────────────────────────────
    /** Full path to the last .txt file saved by saveList() — enables Print/PDF buttons. */
    private String lastSavedTxtPath = null;
    private JButton printReceiptBtn;
    private JButton savePdfBtn;

    // ── Feature: Autosave ─────────────────────────────────────────────────────
    private Timer autosaveTimer;

    // ── Feature: Undo ────────────────────────────────────────────────────────
    private TradeItem lastAddedItem = null;
    private int lastAddedRow = -1;
    private JButton undoBtn;

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

    /**
     * Constructs the Trade Receiving panel, initialises all sub-panels and UI components,
     * and registers global keyboard shortcuts (Numpad+, Ctrl+Z, F1).
     */
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
                // keyTyped handles '+' when the code field has focus; skip here to avoid double-fire
                if (!cardCodeField.isFocusOwner()) {
                    duplicateSelectedCard();
                }
            }
        });

        // Ctrl+Z → undo last added card
        panelIM.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "globalUndo");
        panelAM.put("globalUndo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                undoLastCard();
            }
        });

        // F1 → shortcut help dialog
        panelIM.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "showHelp");
        panelAM.put("showHelp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ShortcutHelpDialog.show(SwingUtilities.getWindowAncestor(TradePanel.this),
                        HELP_TITLE, HELP_COLS, HELP_ROWS);
            }
        });

        // F4 → vintage set reference dialog
        panelIM.put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), "showVintage");
        panelAM.put("showVintage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showVintageReference();
            }
        });

        // / → jump to card code field
        panelIM.put(KeyStroke.getKeyStroke('/'), "focusCodeField");
        panelAM.put("focusCodeField", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cardCodeField.requestFocusInWindow();
            }
        });

        SwingUtilities.invokeLater(() -> cardCodeField.requestFocusInWindow());

        // F5: offer crash-recovery restore, then start 60-second autosave timer
        SwingUtilities.invokeLater(this::offerSessionRestore);
        autosaveTimer = new Timer(60_000, e -> performAutosave());
        autosaveTimer.setRepeats(true);
        autosaveTimer.start();

        // Reload buy rates when this panel becomes visible (Preferences may have changed)
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                int gen = BuyRateService.getSaveGeneration();
                if (gen != lastKnownBuyRateGen) {
                    lastKnownBuyRateGen = gen;
                    buyRateService.reload();
                    refreshSummary();
                }
            }
        });
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
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == '+') {
                    e.consume(); // prevent '+' from being inserted into the field
                    duplicateSelectedCard();
                }
            }

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

        // Table with checkbox, Condition, Qty, Unit Price, Total, and Rate columns
        String[] columns = {"☑", "Code", "Card Name", "Condition", "Qty", "Unit Price", "Total", "Rate"};
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
        cardTable.getColumnModel().getColumn(7).setPreferredWidth(110); // Rate

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
        sorter.setSortable(7, false);

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

        // F1+F2: Register bounty-aware renderer on all non-checkbox columns (cols 1–7)
        BountyAwareRenderer bountyRenderer = new BountyAwareRenderer();
        for (int c = 1; c <= 7; c++) {
            cardTable.getColumnModel().getColumn(c).setCellRenderer(bountyRenderer);
        }

        // F7: Restore saved column widths from preferences
        restoreColumnWidths();

        // F7: Persist column widths whenever the user resizes a column
        cardTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override public void columnMarginChanged(ChangeEvent e) {
                Preferences prefs = Preferences.userNodeForPackage(PreferencesPanel.class);
                for (int i = 0; i < cardTable.getColumnCount(); i++) {
                    prefs.putInt("trade.table.col." + i,
                            cardTable.getColumnModel().getColumn(i).getWidth());
                }
            }
            @Override public void columnAdded(TableColumnModelEvent e) {}
            @Override public void columnRemoved(TableColumnModelEvent e) {}
            @Override public void columnMoved(TableColumnModelEvent e) {}
            @Override public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) {}
        });

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
                    if (modelRow < rowPayouts.size()) rowPayouts.remove(modelRow);
                    tableModel.removeRow(modelRow);
                    clearUndoState();
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

        // ── Row 1: Search | Clear | Undo | Vintage Sets ───────────────────────
        JButton searchBtn  = new JButton("Search by Name (Ctrl+F)");
        JButton clearBtn   = new JButton("Clear All");
        undoBtn = new JButton("Undo (Ctrl+Z)");
        undoBtn.setEnabled(false);
        JButton vintageBtn = new JButton("Vintage Sets (F4)");

        // ── Row 2: Export POS | Save List | Print Receipt | Save as PDF ───────
        JButton exportInventoryBtn = new JButton("Export to POS (CSV)");
        JButton saveListBtn        = new JButton("Save Card List");
        printReceiptBtn = new JButton("Print Receipt");
        savePdfBtn      = new JButton("Save as PDF");
        printReceiptBtn.setEnabled(false);
        savePdfBtn.setEnabled(false);

        // Apply consistent sizing
        for (JButton btn : new JButton[]{
                searchBtn, clearBtn, undoBtn, vintageBtn,
                exportInventoryBtn, saveListBtn, printReceiptBtn, savePdfBtn}) {
            btn.setFocusPainted(false);
            btn.setPreferredSize(new Dimension(165, 36));
        }

        searchBtn.addActionListener(e -> openSearchDialog());
        clearBtn.addActionListener(e -> clearAll());
        vintageBtn.addActionListener(e -> showVintageReference());
        undoBtn.addActionListener(e -> undoLastCard());
        exportInventoryBtn.addActionListener(e -> exportToPOS());
        saveListBtn.addActionListener(e -> saveList());
        printReceiptBtn.addActionListener(e -> doPrintReceipt());
        savePdfBtn.addActionListener(e -> doSaveAsPdf());

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row1.add(searchBtn);
        row1.add(clearBtn);
        row1.add(undoBtn);
        row1.add(vintageBtn);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row2.add(exportInventoryBtn);
        row2.add(saveListBtn);
        row2.add(printReceiptBtn);
        row2.add(savePdfBtn);

        JPanel buttonArea = new JPanel();
        buttonArea.setLayout(new BoxLayout(buttonArea, BoxLayout.Y_AXIS));
        buttonArea.add(row1);
        buttonArea.add(Box.createVerticalStrut(8));
        buttonArea.add(row2);

        panel.add(buttonArea, BorderLayout.WEST);

        // ── Right: [?] help button + summary ──────────────────────────────────
        JButton helpBtn = new JButton("?");
        helpBtn.setFocusPainted(false);
        helpBtn.setPreferredSize(new Dimension(34, 34));
        helpBtn.setFont(helpBtn.getFont().deriveFont(Font.BOLD, 14f));
        helpBtn.setToolTipText("Help (F1)");
        helpBtn.addActionListener(e ->
                ShortcutHelpDialog.show(SwingUtilities.getWindowAncestor(this),
                        HELP_TITLE, HELP_COLS, HELP_ROWS));

        JPanel helpRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        helpRow.add(helpBtn);

        JPanel rightPanel = new JPanel(new BorderLayout(4, 4));
        rightPanel.add(helpRow, BorderLayout.NORTH);
        rightPanel.add(summaryPanel, BorderLayout.CENTER);

        panel.add(rightPanel, BorderLayout.EAST);

        // Highlight the initial payment rate
        summaryPanel.update(BigDecimal.ZERO, 0, paymentTypePanel.getPaymentType(),
                BigDecimal.ZERO, BigDecimal.ZERO);

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

                    // Feature 8: auto-show card image for vintage sets so the
                    // trader can visually verify the card before committing.
                    if (VintageUtil.isVintageSet(card.getSetCode()) && card.getImageUrl() != null) {
                        try {
                            Point labelLoc = cardPreviewLabel.getLocationOnScreen();
                            getImagePopup().show(card.getImageUrl(),
                                    new Point(labelLoc.x + cardPreviewLabel.getWidth() + 12,
                                              labelLoc.y));
                        } catch (java.awt.IllegalComponentStateException ignored) {}
                    }

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
                    String.format("$%.2f", price), // Total
                    ""      // Rate placeholder
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
            rowPayouts.add(null);

            refreshSummary();

            // Auto-highlight and scroll to the newly added row
            int viewRow = cardTable.convertRowIndexToView(tableModel.getRowCount() - 1);
            cardTable.setRowSelectionInterval(viewRow, viewRow);
            cardTable.scrollRectToVisible(cardTable.getCellRect(viewRow, 0, true));

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
                    String.format("$%.2f", price), // Total
                    ""      // Rate placeholder
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
            rowPayouts.add(null);

            refreshSummary();

            // Auto-highlight and scroll to the newly added row
            int viewRow = cardTable.convertRowIndexToView(tableModel.getRowCount() - 1);
            cardTable.setRowSelectionInterval(viewRow, viewRow);
            cardTable.scrollRectToVisible(cardTable.getCellRect(viewRow, 0, true));

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
        rowPayouts.add(null); // placeholder; overwritten immediately by refreshSummary()

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

        // Feature 7: high-value confirmation — pause and verify before adding
        if (roundedPrice.compareTo(VintageUtil.HIGH_VALUE_THRESHOLD) >= 0) {
            if (!confirmHighValueAdd(card, roundedPrice)) {
                // User cancelled — roll back the optimistic list additions
                receivedCards.remove(receivedCards.size() - 1);
                cardConditions.remove(cardConditions.size() - 1);
                rowPayouts.remove(rowPayouts.size() - 1);
                return;
            }
        }

        tableModel.addRow(new Object[]{
                false,  // Checkbox unchecked by default
                code,
                name.toString(),
                "NM", // Default condition
                1,    // Default qty = 1
                String.format("$%.2f", roundedPrice), // Unit price
                String.format("$%.2f", roundedPrice), // Total (qty * unit price)
                ""    // Rate placeholder; set by refreshSummary()
        });

        refreshSummary();

        // Track undo state for the card just added
        lastAddedItem = item;
        lastAddedRow  = tableModel.getRowCount() - 1;
        if (undoBtn != null) undoBtn.setEnabled(true);

        // Auto-highlight and scroll to the newly added row
        int viewRow = cardTable.convertRowIndexToView(tableModel.getRowCount() - 1);
        cardTable.setRowSelectionInterval(viewRow, viewRow);
        cardTable.scrollRectToVisible(cardTable.getCellRect(viewRow, 0, true));

        cardCodeField.setText("");
        clearPreview();
        cardCodeField.requestFocusInWindow();
    }

    /** Removes the most recently added card. Single-level undo. */
    private void undoLastCard() {
        if (lastAddedItem == null) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "Nothing to undo.", "Undo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int row = lastAddedRow;
        if (row >= 0 && row < tableModel.getRowCount()) {
            receivedCards.remove(row);
            cardConditions.remove(row);
            if (row < rowPayouts.size()) rowPayouts.remove(row);
            tableModel.removeRow(row);
        }
        clearUndoState();
        refreshSummary();
    }

    private void clearUndoState() {
        lastAddedItem = null;
        lastAddedRow  = -1;
        if (undoBtn != null) undoBtn.setEnabled(false);
    }

    private void removeSelected() {
        int row = cardTable.getSelectedRow();
        if (row >= 0) {
            // Convert view row to model row since table might be sorted
            int modelRow = cardTable.convertRowIndexToModel(row);
            receivedCards.remove(modelRow);
            cardConditions.remove(modelRow);
            tableModel.removeRow(modelRow);
            clearUndoState();
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
        rowPayouts.add(null); // placeholder; overwritten by refreshSummary()

        // Add to table
        tableModel.addRow(new Object[]{
                false,  // Checkbox
                code,
                name,
                condition,
                qty,
                unitPrice,
                String.format("$%.2f", total),
                ""      // Rate placeholder
        });

        refreshSummary();

        // Select the newly added duplicate
        int viewRow = cardTable.convertRowIndexToView(tableModel.getRowCount() - 1);
        cardTable.setRowSelectionInterval(viewRow, viewRow);
        cardTable.scrollRectToVisible(cardTable.getCellRect(viewRow, 0, true));
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
                if (row < rowPayouts.size()) rowPayouts.remove(row);
                tableModel.removeRow(row);
            }
            clearUndoState();
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
        if (isRefreshingSummary) return;
        isRefreshingSummary = true;
        try {
            refreshSummaryImpl();
        } finally {
            isRefreshingSummary = false;
        }
    }

    private void refreshSummaryImpl() {
        // Reload buy rates if Preferences changed them since the last refresh
        int gen = BuyRateService.getSaveGeneration();
        if (gen != lastKnownBuyRateGen) {
            lastKnownBuyRateGen = gen;
            buyRateService.reload();
        }

        BigDecimal total       = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal totalCheck  = BigDecimal.ZERO;
        int totalQty = 0;

        int rowCount = tableModel.getRowCount();

        // Sync rowPayouts size to match current row count
        while (rowPayouts.size() < rowCount) rowPayouts.add(null);
        while (rowPayouts.size() > rowCount) rowPayouts.remove(rowPayouts.size() - 1);

        for (int i = 0; i < rowCount; i++) {
            // Parse unit price (Column 5)
            BigDecimal unitPrice;
            try {
                String unitPriceStr = (String) tableModel.getValueAt(i, 5);
                unitPrice = new BigDecimal(unitPriceStr.replace("$", "").replace(",", "").trim());
            } catch (Exception e) {
                continue; // skip unparseable row
            }

            // Parse quantity (Column 4)
            int qty;
            try {
                Object qtyObj = tableModel.getValueAt(i, 4);
                qty = (qtyObj instanceof Integer) ? (Integer) qtyObj : Integer.parseInt(qtyObj.toString());
            } catch (Exception e) {
                qty = 1;
            }

            total = total.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
            totalQty += qty;

            // Look up tiered payout for this card
            String setCode   = null;
            String collNum   = null;
            String cardName  = null;
            if (i < receivedCards.size()) {
                com.cardpricer.model.Card card = receivedCards.get(i).getCard();
                setCode  = card.getSetCode();
                collNum  = card.getCollectorNumber();
                cardName = card.getName();
            }

            BuyRateService.PayoutResult result =
                    buyRateService.computePayout(setCode, collNum, cardName, unitPrice);
            totalCredit = totalCredit.add(result.creditPayout().multiply(BigDecimal.valueOf(qty)));
            totalCheck  = totalCheck.add(result.checkPayout().multiply(BigDecimal.valueOf(qty)));

            // Store result for bounty tint renderer and update Rate column (col 7)
            rowPayouts.set(i, result);
            String creditPct = String.format("%.0f",
                    result.appliedCreditRate().multiply(new BigDecimal("100")));
            String checkPct  = String.format("%.0f",
                    result.appliedCheckRate().multiply(new BigDecimal("100")));
            String rateStr   = (result.isBounty() ? "\u2605 " : "") + creditPct + "% / " + checkPct + "%";
            tableModel.setValueAt(rateStr, i, 7);
        }

        // Scale accumulated payouts to 2 dp
        totalCredit = totalCredit.setScale(2, RoundingMode.HALF_UP);
        totalCheck  = totalCheck.setScale(2, RoundingMode.HALF_UP);

        // Store for saveList() to pass to saveCardList()
        lastTierCreditTotal = totalCredit;
        lastTierCheckTotal  = totalCheck;

        // Update PaymentTypePanel with tiered seeds for partial split
        paymentTypePanel.setTotal(total, totalCredit, totalCheck);

        // Update summary labels
        summaryPanel.update(total, totalQty, paymentTypePanel.getPaymentType(), totalCredit, totalCheck);

        // Repaint the whole table so the BountyAwareRenderer can apply gold tints
        // to ALL columns (not just col 7 which gets a cell-update event each iteration).
        if (cardTable != null) cardTable.repaint();
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
            rowPayouts.clear();
            tableModel.setRowCount(0);
            clearUndoState();
            lastSavedTxtPath = null;
            if (printReceiptBtn != null) printReceiptBtn.setEnabled(false);
            if (savePdfBtn != null) savePdfBtn.setEnabled(false);
            TradeSessionService.clearAutosave();
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

        if (!confirmProceedWithoutNames()) return;

        String traderName = traderNameField.getText().trim();
        if (traderName.isEmpty()) {
            traderName = "Unknown";
        }
        String customerName = customerNameField.getText().trim();
        if (customerName.isEmpty()) {
            customerName = "Unknown";
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
                    nonMiscCards, traderName, customerName, nonMiscUnitPrices, nonMiscQuantities, paymentType);

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

        if (!confirmProceedWithoutNames()) return;

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
            // Use new method with table values and tiered payout totals
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
                    quantities,
                    lastTierCreditTotal,
                    lastTierCheckTotal
            );

            // Remember path so Print/PDF buttons can read it
            lastSavedTxtPath = filename;
            if (printReceiptBtn != null) printReceiptBtn.setEnabled(true);
            if (savePdfBtn != null) savePdfBtn.setEnabled(true);

            TradeSessionService.clearAutosave();

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

    /** Opens the vintage set reference dialog via the shared ShortcutHelpDialog infrastructure. */
    private void showVintageReference() {
        ShortcutHelpDialog.show(getParentWindow(),
                "Vintage Set Reference  —  Codes & Name Shortcuts",
                VintageUtil.REF_COLUMNS, VintageUtil.REF_ROWS);
    }

    /**
     * Shows a modal confirmation dialog for high-value cards.
     * Loads the card image asynchronously inside the dialog while the user reviews.
     *
     * @param card  the card about to be added
     * @param price the computed rounded price
     * @return {@code true} if the user confirmed; {@code false} to cancel
     */
    private boolean confirmHighValueAdd(Card card, BigDecimal price) {
        JDialog dialog = new JDialog(getParentWindow(),
                "Verify High-Value Card", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel main = new JPanel(new BorderLayout(14, 10));
        main.setBorder(new EmptyBorder(16, 16, 12, 16));

        // ── Left: card image ──────────────────────────────────────────────────
        int imgW = 200;
        int imgH = (int) (imgW * 1.396);
        JLabel imgLabel = new JLabel("<html><center style='color:gray;'>Loading…</center></html>",
                SwingConstants.CENTER);
        imgLabel.setPreferredSize(new Dimension(imgW, imgH));
        imgLabel.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));

        if (card.getImageUrl() != null) {
            final String imgUrl = card.getImageUrl();
            new SwingWorker<ImageIcon, Void>() {
                @Override protected ImageIcon doInBackground() throws Exception {
                    java.awt.image.BufferedImage raw = ImageIO.read(new URL(imgUrl));
                    if (raw == null) return null;
                    int h = raw.getHeight() * imgW / raw.getWidth();
                    return new ImageIcon(raw.getScaledInstance(imgW, h, java.awt.Image.SCALE_SMOOTH));
                }
                @Override protected void done() {
                    try {
                        ImageIcon icon = get();
                        if (icon != null) {
                            imgLabel.setIcon(icon);
                            imgLabel.setText(null);
                            imgLabel.setPreferredSize(null);
                            dialog.pack();
                        }
                    } catch (Exception ignored) {}
                }
            }.execute();
        }

        // ── Centre: card info ─────────────────────────────────────────────────
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(0, 10, 0, 0));

        JLabel warnLabel = new JLabel("High-Value Card — Please Verify");
        warnLabel.setFont(warnLabel.getFont().deriveFont(Font.BOLD, 13f));
        warnLabel.setForeground(new Color(180, 100, 0));

        JLabel nameLabel = new JLabel(card.getName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 18f));

        String setInfo = card.getSetCode() + "  #" + card.getCollectorNumber();
        if (card.isReserved()) setInfo += "  [Reserved List]";
        JLabel setLabel = new JLabel(setInfo);
        setLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JLabel priceLabel = new JLabel(String.format("Market Value:  $%.2f", price));
        priceLabel.setFont(priceLabel.getFont().deriveFont(Font.BOLD, 22f));
        priceLabel.setForeground(new Color(0, 140, 0));

        JLabel promptLabel = new JLabel(
                "<html>Physically verify the card before adding it to this trade.</html>");
        promptLabel.setFont(promptLabel.getFont().deriveFont(Font.PLAIN, 12f));

        info.add(warnLabel);
        info.add(Box.createVerticalStrut(10));
        info.add(nameLabel);
        info.add(Box.createVerticalStrut(4));
        info.add(setLabel);
        info.add(Box.createVerticalStrut(14));
        info.add(priceLabel);
        info.add(Box.createVerticalStrut(14));
        info.add(promptLabel);

        // ── Bottom: buttons ───────────────────────────────────────────────────
        boolean[] confirmed = {false};

        JButton addBtn = new JButton("Add to Trade");
        addBtn.setFocusPainted(false);
        addBtn.putClientProperty("JButton.buttonType", "roundRect");
        addBtn.addActionListener(e -> { confirmed[0] = true; dialog.dispose(); });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setFocusPainted(false);
        cancelBtn.putClientProperty("JButton.buttonType", "roundRect");
        cancelBtn.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelBtn);
        buttons.add(addBtn);

        main.add(imgLabel,  BorderLayout.WEST);
        main.add(info,      BorderLayout.CENTER);
        main.add(buttons,   BorderLayout.SOUTH);

        dialog.setContentPane(main);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(500, 300));
        dialog.setLocationRelativeTo(getParentWindow());
        dialog.setVisible(true); // blocks until closed (modal)

        return confirmed[0];
    }

    private CardImagePopup getImagePopup() {
        if (imagePopup == null) {
            imagePopup = new CardImagePopup(SwingUtilities.getWindowAncestor(this));
        }
        return imagePopup;
    }

    // -------------------------------------------------------------------------
    // Feature: Print Receipt / Save as PDF
    // -------------------------------------------------------------------------

    private void doPrintReceipt() {
        if (lastSavedTxtPath == null) return;
        try {
            String content = Files.readString(Path.of(lastSavedTxtPath));
            ReceiptPrintService.printReceipt(this, content);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "Could not read receipt file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doSaveAsPdf() {
        if (lastSavedTxtPath == null) return;
        try {
            String content = Files.readString(Path.of(lastSavedTxtPath));
            String pdfPath = lastSavedTxtPath.replace(".txt", ".pdf");
            ReceiptPrintService.saveAsPdf(this, content, pdfPath);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "Could not read receipt file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Feature: Warn on missing customer/trader name
    // -------------------------------------------------------------------------

    /**
     * Checks whether customer and trader name fields are filled.
     * If either is blank, shows a YES/NO warning dialog.
     * Choosing NO highlights the empty field(s) with an orange border and returns false.
     *
     * @return {@code true} if the caller should proceed; {@code false} to abort.
     */
    private boolean confirmProceedWithoutNames() {
        List<String> missing = new ArrayList<>();
        if (customerNameField.getText().isBlank()) missing.add("Customer Name");
        if (traderNameField.getText().isBlank())   missing.add("Trader Name");
        if (missing.isEmpty()) return true;

        int result = JOptionPane.showConfirmDialog(this,
                "The following fields are empty: " + String.join(", ", missing)
                + "\n\nProceed anyway?",
                "Missing Information",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) return true;

        // Highlight the empty fields so the user knows what to fill in
        if (customerNameField.getText().isBlank()) highlightEmptyField(customerNameField);
        if (traderNameField.getText().isBlank())   highlightEmptyField(traderNameField);
        return false;
    }

    /** Draws an orange border on {@code field} and auto-removes it when the user types. */
    private void highlightEmptyField(JTextField field) {
        javax.swing.border.Border original = field.getBorder();
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 140, 0), 2),
                new EmptyBorder(2, 4, 2, 4)));
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { field.setBorder(original); }
            @Override public void removeUpdate(DocumentEvent e)  { field.setBorder(original); }
            @Override public void changedUpdate(DocumentEvent e) {}
        });
    }

    // -------------------------------------------------------------------------
    // F10: Unsaved-trade check for window-close confirmation
    // -------------------------------------------------------------------------

    /** Returns {@code true} if there are cards in the table that have not been saved. */
    public boolean hasUnsavedCards() {
        return !receivedCards.isEmpty();
    }

    // -------------------------------------------------------------------------
    // F7: Column width memory
    // -------------------------------------------------------------------------

    /** Restores saved column widths from Preferences. */
    private void restoreColumnWidths() {
        Preferences prefs = Preferences.userNodeForPackage(PreferencesPanel.class);
        for (int i = 0; i < cardTable.getColumnCount(); i++) {
            int w = prefs.getInt("trade.table.col." + i, -1);
            if (w > 0) cardTable.getColumnModel().getColumn(i).setPreferredWidth(w);
        }
    }

    // -------------------------------------------------------------------------
    // F5: Autosave / crash recovery
    // -------------------------------------------------------------------------

    /** Offers to restore a previously crashed session (called on first EDT tick). */
    private void offerSessionRestore() {
        if (!TradeSessionService.hasAutosave()) return;
        int choice = JOptionPane.showConfirmDialog(getParentWindow(),
                "An unsaved trade session was found.\nWould you like to restore it?",
                "Restore Session",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            TradeSessionService.SavedSession session = TradeSessionService.load();
            if (session != null) restoreSession(session);
        } else {
            TradeSessionService.clearAutosave();
        }
    }

    /** Restores a previously saved session into the trade table. */
    private void restoreSession(TradeSessionService.SavedSession session) {
        if (session == null) return;
        if (session.traderName() != null) traderNameField.setText(session.traderName());
        if (session.customerName() != null) customerNameField.setText(session.customerName());

        for (TradeSessionService.SessionRow row : session.rows()) {
            String[] codeParts = row.code().split(" ", 2);
            String setCode = codeParts.length > 0 ? codeParts[0] : "MISC";
            String collNum = codeParts.length > 1 ? codeParts[1] : "1";

            Card stub = new Card();
            stub.setName(row.cardName());
            stub.setSetCode(setCode);
            stub.setCollectorNumber(collNum);
            stub.setRarity("common");
            stub.setPrice(row.unitPrice().toPlainString());

            TradeItem item = new TradeItem(stub, false, row.qty());
            item.setUnitPrice(row.unitPrice());
            item.setQuantity(row.qty());

            receivedCards.add(item);
            cardConditions.add(row.condition());
            rowPayouts.add(null);

            BigDecimal total = row.unitPrice().multiply(BigDecimal.valueOf(row.qty()));
            tableModel.addRow(new Object[]{
                    false,
                    row.code(),
                    row.cardName(),
                    row.condition(),
                    row.qty(),
                    String.format("$%.2f", row.unitPrice()),
                    String.format("$%.2f", total),
                    ""
            });
        }
        refreshSummary();

        // Fetch image URLs for restored cards in the background so the hover
        // popup works on the restored rows (stubs have no imageUrl yet).
        List<Card> stubs = new ArrayList<>();
        for (TradeItem item : receivedCards) stubs.add(item.getCard());
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                for (Card stub : stubs) {
                    if ("MISC".equalsIgnoreCase(stub.getSetCode())) continue;
                    try {
                        Card fetched = apiService.fetchCard(stub.getSetCode(), stub.getCollectorNumber());
                        stub.setImageUrl(fetched.getImageUrl());
                    } catch (Exception ignored) {}
                    try { Thread.sleep(110); } catch (InterruptedException ignored) { break; }
                }
                return null;
            }
        }.execute();
    }

    /** Writes current table contents to the autosave file. */
    private void performAutosave() {
        if (tableModel.getRowCount() == 0) {
            TradeSessionService.clearAutosave();
            return;
        }
        List<TradeSessionService.SessionRow> rows = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String code = (String) tableModel.getValueAt(i, 1);
            String cardName = (String) tableModel.getValueAt(i, 2);
            String condition = (String) tableModel.getValueAt(i, 3);
            Object qtyObj = tableModel.getValueAt(i, 4);
            int qty = (qtyObj instanceof Integer) ? (Integer) qtyObj : Integer.parseInt(qtyObj.toString());
            String priceStr = ((String) tableModel.getValueAt(i, 5))
                    .replace("$", "").replace(",", "").trim();
            BigDecimal unitPrice;
            try { unitPrice = new BigDecimal(priceStr); } catch (Exception e) { continue; }
            rows.add(new TradeSessionService.SessionRow(code, cardName, condition, qty, unitPrice));
        }
        TradeSessionService.save(
                traderNameField.getText().trim(),
                customerNameField.getText().trim(),
                rows);
    }

    // -------------------------------------------------------------------------
    // F1+F2: Bounty-aware row tint renderer
    // -------------------------------------------------------------------------

    /**
     * Renders table cells with a gold background for rows whose payout came from a bounty override.
     */
    private class BountyAwareRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                if (modelRow >= 0 && modelRow < rowPayouts.size()
                        && rowPayouts.get(modelRow) != null
                        && rowPayouts.get(modelRow).isBounty()) {
                    c.setBackground(new Color(42, 122, 122));
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(table.getBackground());
                    c.setForeground(table.getForeground());
                }
            }
            return c;
        }
    }
}
