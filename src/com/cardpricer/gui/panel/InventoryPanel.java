package com.cardpricer.gui.panel;

import com.cardpricer.exception.ScryfallApiException;
import com.cardpricer.model.Card;
import com.cardpricer.model.CardEntry;
import com.cardpricer.service.CsvExportService;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.util.SetList;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel for updating inventory quantities for cards in a set
 */
public class InventoryPanel extends JPanel {

    private final ScryfallApiService apiService;
    private static final String DATA_DIRECTORY = "data/inventory";

    // UI Components
    private JComboBox<String> setComboBox;
    private JButton loadSetButton;
    private JTable cardTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JButton exportButton;
    private JButton clearQuantitiesButton;

    private List<Card> loadedCards;
    private List<Integer> quantities; // Tracks quantity for each card

    public InventoryPanel() {
        this.apiService = new ScryfallApiService();
        this.loadedCards = new ArrayList<>();
        this.quantities = new ArrayList<>();

        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Title section
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Inventory Update");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Select a set and update card quantities for inventory");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));

        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subtitle);

        panel.add(titlePanel, BorderLayout.NORTH);

        // Set selection section
        JPanel selectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        selectionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Set Selection"),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel setLabel = new JLabel("Select Set:");

        // Populate set dropdown with all available sets
        String[] sets = SetList.ALL_SETS_CUSTOM_CODES.toArray(new String[0]);
        setComboBox = new JComboBox<>(sets);
        setComboBox.setPreferredSize(new Dimension(150, 32));

        loadSetButton = new JButton("Load Set Cards");
        loadSetButton.setFocusPainted(false);
        loadSetButton.setPreferredSize(new Dimension(140, 32));
        loadSetButton.addActionListener(e -> loadSet());

        selectionPanel.add(setLabel);
        selectionPanel.add(setComboBox);
        selectionPanel.add(loadSetButton);

        panel.add(selectionPanel, BorderLayout.CENTER);

        // Status section
        JPanel statusPanel = new JPanel(new BorderLayout(5, 5));
        statusLabel = new JLabel("Select a set and click 'Load Set Cards' to begin");
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        statusPanel.add(statusLabel, BorderLayout.NORTH);
        statusPanel.add(progressBar, BorderLayout.CENTER);

        panel.add(statusPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Table columns: Code, Card Name, Normal Price, Foil Price, Normal Qty, Foil Qty
        String[] columns = {"Code", "Card Name", "Normal Price", "Foil Price", "Normal Qty", "Foil Qty"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4 || column == 5; // Only quantity columns are editable
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 4 || column == 5) {
                    return Integer.class; // Quantity columns
                }
                return super.getColumnClass(column);
            }
        };

        cardTable = new JTable(tableModel);
        cardTable.setFont(cardTable.getFont().deriveFont(14f));
        cardTable.setRowHeight(28);
        cardTable.getColumnModel().getColumn(0).setPreferredWidth(100); // Code
        cardTable.getColumnModel().getColumn(1).setPreferredWidth(300); // Card Name
        cardTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Normal Price
        cardTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Foil Price
        cardTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Normal Qty
        cardTable.getColumnModel().getColumn(5).setPreferredWidth(80);  // Foil Qty

        // Custom cell editor for quantity columns that selects all on focus
        JTextField qtyEditor = new JTextField();
        qtyEditor.setHorizontalAlignment(JTextField.CENTER);
        qtyEditor.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                SwingUtilities.invokeLater(() -> qtyEditor.selectAll());
            }
        });

        DefaultCellEditor quantityEditor = new DefaultCellEditor(qtyEditor) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                                                         boolean isSelected, int row, int column) {
                JTextField editor = (JTextField) super.getTableCellEditorComponent(
                        table, value, isSelected, row, column);
                editor.selectAll();
                return editor;
            }

            @Override
            public Object getCellEditorValue() {
                String value = (String) super.getCellEditorValue();
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }

            @Override
            public boolean stopCellEditing() {
                String value = (String) super.getCellEditorValue();
                try {
                    // Validate it's a valid integer
                    int qty = Integer.parseInt(value.trim());
                    if (qty < 0) {
                        JOptionPane.showMessageDialog(cardTable,
                                "Quantity cannot be negative",
                                "Invalid Quantity",
                                JOptionPane.WARNING_MESSAGE);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(cardTable,
                            "Please enter a valid number",
                            "Invalid Quantity",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                return super.stopCellEditing();
            }
        };

        cardTable.getColumnModel().getColumn(4).setCellEditor(quantityEditor); // Normal Qty
        cardTable.getColumnModel().getColumn(5).setCellEditor(quantityEditor); // Foil Qty

        // Enable table sorting
        cardTable.setAutoCreateRowSorter(true);

        JScrollPane scrollPane = new JScrollPane(cardTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Set Cards (Click column headers to sort)"));

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Left side - empty for now (removed export format selection)
        JPanel leftPanel = new JPanel();
        panel.add(leftPanel, BorderLayout.WEST);

        // Right: Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        clearQuantitiesButton = new JButton("Clear All Quantities");
        clearQuantitiesButton.setFocusPainted(false);
        clearQuantitiesButton.setPreferredSize(new Dimension(160, 36));
        clearQuantitiesButton.addActionListener(e -> clearAllQuantities());
        clearQuantitiesButton.setEnabled(false);

        exportButton = new JButton("Export Inventory");
        exportButton.setFocusPainted(false);
        exportButton.setPreferredSize(new Dimension(140, 36));
        exportButton.addActionListener(e -> exportInventory());
        exportButton.setEnabled(false);

        buttonPanel.add(clearQuantitiesButton);
        buttonPanel.add(exportButton);

        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    private void loadSet() {
        String selectedSet = (String) setComboBox.getSelectedItem();
        if (selectedSet == null) {
            return;
        }

        // Clear existing data
        tableModel.setRowCount(0);
        loadedCards.clear();
        quantities.clear();

        // Disable buttons during load
        loadSetButton.setEnabled(false);
        exportButton.setEnabled(false);
        clearQuantitiesButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        statusLabel.setText("Loading cards from set " + selectedSet + "...");

        SwingWorker<List<Card>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Card> doInBackground() throws Exception {
                return apiService.fetchCardsFromSet(selectedSet);
            }

            @Override
            protected void done() {
                try {
                    List<Card> cards = get();
                    loadedCards = cards;
                    populateTable(cards);

                    statusLabel.setText(String.format("Loaded %d cards from set %s", cards.size(), selectedSet));
                    exportButton.setEnabled(true);
                    clearQuantitiesButton.setEnabled(true);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(InventoryPanel.this,
                            "Failed to load set: " + e.getMessage(),
                            "Load Error",
                            JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Failed to load set");
                } finally {
                    progressBar.setVisible(false);
                    loadSetButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void populateTable(List<Card> cards) {
        for (Card card : cards) {
            String code = card.getSetCode() + " " + card.getCollectorNumber();

            String normalPrice = card.hasNormalPrice() ? "$" + card.getPrice() : "N/A";
            String foilPrice = card.hasFoilPrice() ? "$" + card.getFoilPrice() : "N/A";

            tableModel.addRow(new Object[]{
                    code,
                    card.getName(),
                    normalPrice,
                    foilPrice,
                    0, // Normal quantity default
                    0  // Foil quantity default
            });

            // Initialize quantity tracking (normal and foil per card)
            quantities.add(0); // Normal qty
            quantities.add(0); // Foil qty
        }
    }

    private void clearAllQuantities() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Clear all quantities and reset to 0?",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                tableModel.setValueAt(0, i, 4); // Normal qty
                tableModel.setValueAt(0, i, 5); // Foil qty
            }
            statusLabel.setText("All quantities cleared");
        }
    }

    private void exportInventory() {
        if (loadedCards.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No cards loaded. Please load a set first.",
                    "Nothing to Export",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedSet = (String) setComboBox.getSelectedItem();

        try {
            String filename = exportToChangeQtyFormat(selectedSet);

            int totalCards = 0;
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                totalCards += getQuantity(i, 4);
                totalCards += getQuantity(i, 5);
            }

            JOptionPane.showMessageDialog(this,
                    String.format("Inventory export complete!\n\n" +
                                    "File: %s\n" +
                                    "Total Cards with Quantity: %d\n" +
                                    "Format: Item Wizard Change Qty",
                            filename, totalCards),
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);

            statusLabel.setText("Export completed successfully");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Export failed: " + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private String exportToChangeQtyFormat(String setCode) throws IOException {
        ensureDataDirectoryExists();

        String timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("%s/inventory_%s_%s.csv",
                DATA_DIRECTORY, setCode, timestamp);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // No header for Item Wizard Change Qty format

            for (int i = 0; i < tableModel.getRowCount(); i++) {
                Card card = loadedCards.get(i);
                int normalQty = getQuantity(i, 4);
                int foilQty = getQuantity(i, 5);

                // Always export normal version (even if quantity is 0)
                if (card.hasNormalPrice()) {
                    String code = card.getSetCode() + " " + card.getCollectorNumber();
                    String cardName = card.getName();
                    String artist = card.getArtist() != null ? card.getArtist() : "";

                    writer.println(formatChangeQtyRow(code, cardName, artist, normalQty));
                }

                // Always export foil version (even if quantity is 0)
                if (card.hasFoilPrice()) {
                    String code = card.getSetCode() + " " + card.getCollectorNumber() + "F";
                    String cardName = card.getName();
                    String artist = card.getArtist() != null ? card.getArtist() : "";

                    writer.println(formatChangeQtyRow(code, cardName, artist, foilQty));
                }
            }
        }

        return filename;
    }

    /**
     * Formats a row for Item Wizard Change Qty format
     * Format: CODE,DESCRIPTION,EXTENDED DESCRIPTION,ON_HAND-QTY,NEW ON-HAND QTY
     */
    private String formatChangeQtyRow(String code, String cardName, String artist, int newQty) {
        // Escape and quote card name if needed
        String escapedName = cardName.replace("\"", "\"\"");
        String description = escapedName.contains(",") ? "\"" + escapedName + "\"" : escapedName;

        // Escape and quote artist if needed
        String escapedArtist = artist.replace("\"", "\"\"");
        String extendedDesc = escapedArtist.contains(",") ? "\"" + escapedArtist + "\"" : escapedArtist;

        // Format: CODE,DESCRIPTION,EXTENDED DESCRIPTION,ON_HAND-QTY,NEW ON-HAND QTY
        // We leave ON_HAND-QTY empty (they'll fill it in from current inventory)
        return String.format("%s,%s,%s,,%d",
                code,
                description,
                extendedDesc,
                newQty);
    }

    private void ensureDataDirectoryExists() {
        java.io.File dataDir = new java.io.File(DATA_DIRECTORY);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * Safely gets an integer value from a table cell, handling both Integer and String types
     */
    private int getQuantity(int row, int column) {
        Object value = tableModel.getValueAt(row, column);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}