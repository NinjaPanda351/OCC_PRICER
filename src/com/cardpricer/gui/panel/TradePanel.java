package com.cardpricer.gui.panel;

import com.cardpricer.exception.ScryfallApiException;
import com.cardpricer.gui.dialog.CardSearchDialog;
import com.cardpricer.model.Card;
import com.cardpricer.model.TradeItem;
import com.cardpricer.service.ScryfallApiService;
import com.cardpricer.service.TradeReceivingExportService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
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

    // Trade info
    private JTextField traderNameField;

    // Preview card
    private Card previewCard;
    private String previewFinish;

    public TradePanel() {
        this.apiService = new ScryfallApiService();
        this.exportService = new TradeReceivingExportService();
        this.receivedCards = new ArrayList<>();

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

        // Top: Trader name
        JPanel traderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        traderPanel.add(new JLabel("Trader/Source:"));
        traderNameField = new JTextField(25);
        traderPanel.add(traderNameField);

        panel.add(traderPanel, BorderLayout.NORTH);

        // Middle: Input field
        JPanel inputPanel = new JPanel(new BorderLayout(10, 5));

        JLabel instructionLabel = new JLabel("<html>Enter card code and press ENTER (e.g., TDM 3, TDM 3F, TDM 3E) | Press <b>Ctrl+F</b> to search by name</html>");
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

        // Table
        String[] columns = {"Code", "Card Name", "Price"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2; // Only price column is editable
            }
        };

        cardTable = new JTable(tableModel);
        cardTable.setFont(cardTable.getFont().deriveFont(14f));
        cardTable.setRowHeight(28);
        cardTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        cardTable.getColumnModel().getColumn(1).setPreferredWidth(400);
        cardTable.getColumnModel().getColumn(2).setPreferredWidth(100);

        // Add cell editor that formats on commit
        cardTable.getColumnModel().getColumn(2).setCellEditor(new javax.swing.DefaultCellEditor(new JTextField()) {
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
                String cardName = (String) tableModel.getValueAt(row, 1);
                int confirm = JOptionPane.showConfirmDialog(
                        cardTable,
                        "Delete \"" + cardName + "\"?",
                        "Confirm Delete",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    receivedCards.remove(row);
                    tableModel.removeRow(row);
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
        scrollPane.setBorder(BorderFactory.createTitledBorder("Received Cards"));

        panel.add(scrollPane, BorderLayout.CENTER);

        // Remove button
        JButton removeBtn = new JButton("Remove Selected");
        removeBtn.putClientProperty("JButton.buttonType", "roundRect");
        removeBtn.addActionListener(e -> removeSelected());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(removeBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Left: Action buttons in 2x2 grid
        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 10, 5));

        JButton searchBtn = new JButton("Search by Name (Ctrl+F)");
        JButton clearBtn = new JButton("Clear All");
        JButton exportInventoryBtn = new JButton("Export to POS (CSV)");
        JButton saveListBtn = new JButton("Save Card List");

        searchBtn.putClientProperty("JButton.buttonType", "roundRect");
        clearBtn.putClientProperty("JButton.buttonType", "roundRect");
        exportInventoryBtn.putClientProperty("JButton.buttonType", "roundRect");
        saveListBtn.putClientProperty("JButton.buttonType", "roundRect");

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

        return panel;
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
                } catch (Exception ex) {
                    cardPreviewLabel.setText("❌ Card not found: " + formatCardCode(parsed));
                    cardPreviewLabel.setForeground(Color.RED);
                    previewCard = null;
                }
            }
        }.execute();
    }

    private void fetchPreviewAndAdd() {
        String input = cardCodeField.getText();
        if (input == null || input.trim().isEmpty() || input.trim().length() < 3) {
            return;
        }

        // Check for MISC special case
        if (input.trim().equalsIgnoreCase("MISC")) {
            addMiscCard();
            return;
        }

        ParsedCode parsed = parseCardCode(input);
        if (parsed == null) {
            JOptionPane.showMessageDialog(this,
                    "Invalid card code format",
                    "Invalid Input",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        cardPreviewLabel.setText("⏳ Fetching card...");
        cardPreviewLabel.setForeground(UIManager.getColor("Label.foreground"));

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
                } catch (Exception ex) {
                    cardPreviewLabel.setText("❌ Card not found: " + formatCardCode(parsed));
                    cardPreviewLabel.setForeground(Color.RED);
                    JOptionPane.showMessageDialog(TradePanel.this,
                            "Card not found: " + formatCardCode(parsed),
                            "Not Found",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void addMiscCard() {
        String priceStr = JOptionPane.showInputDialog(this,
                "Enter price for misc magic card:",
                "Misc Card Price",
                JOptionPane.QUESTION_MESSAGE);

        if (priceStr == null || priceStr.trim().isEmpty()) {
            // User cancelled
            cardCodeField.setText("");
            clearPreview();
            cardCodeField.requestFocusInWindow();
            return;
        }

        try {
            // Parse price
            priceStr = priceStr.replace("$", "").replace(",", "").trim();
            BigDecimal price = new BigDecimal(priceStr);

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

            // Add row directly to table
            tableModel.addRow(new Object[]{
                    "MISC",
                    "Misc Magic Card",
                    String.format("$%.2f", price)
            });

            // Create a dummy TradeItem to keep receivedCards in sync
            // We'll create a minimal Card object for this
            Card miscCard = new Card();
            miscCard.setName("Misc Magic Card");
            miscCard.setSetCode("MISC");
            miscCard.setCollectorNumber("1");
            miscCard.setRarity("common");
            miscCard.setPrice(price.toString());

            TradeItem item = new TradeItem(miscCard, false, 1);
            receivedCards.add(item);

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
                code,
                name.toString(),
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
            receivedCards.remove(row);
            tableModel.removeRow(row);
            updateSummary();
        }
    }

    private void updateSummary() {
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < receivedCards.size(); i++) {
            // Get price directly from table (which may have been manually edited)
            String priceStr = (String) tableModel.getValueAt(i, 2);
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

        try {
            String filename = exportService.exportToPOSFormat(receivedCards, traderName);

            JOptionPane.showMessageDialog(this,
                    String.format("POS import file created!\n\n" +
                                    "File: %s\n" +
                                    "Cards: %d\n" +
                                    "Total Value: $%.2f\n\n" +
                                    "Ready to import into your POS system.",
                            filename,
                            receivedCards.size(),
                            getTotalValue()),
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
        if (traderName.isEmpty()) {
            traderName = "Unknown";
        }

        try {
            String filename = exportService.saveCardList(receivedCards, traderName);

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
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < receivedCards.size(); i++) {
            // Get price from table (may have been manually edited)
            String priceStr = (String) tableModel.getValueAt(i, 2);
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