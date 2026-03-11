package com.cardpricer.gui.panel.filemanager;

import com.cardpricer.model.TradeRecord;
import com.cardpricer.service.ReceiptPrintService;
import com.cardpricer.service.TradeHistoryService;
import com.cardpricer.util.AppDataDirectory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * History tab: browse, search, preview, print, and export past trade receipts.
 */
public final class HistoryTab {

    private static final DateTimeFormatter HISTORY_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private JTable            historyTable;
    private DefaultTableModel historyTableModel;
    private JTextArea         historyPreviewArea;
    private JTextField        historySearchField;
    private JComboBox<String> historyPaymentCombo;
    private JLabel            historyStatusLabel;

    private JLabel historyStatsTotalTrades;
    private JLabel historyStatsTotalCards;
    private JLabel historyStatsTotalValue;
    private JLabel historyStatsAvgValue;

    private List<TradeRecord>  allRecords   = new ArrayList<>();
    private final Map<String, String> contentCache = new HashMap<>();

    private JPanel panel; // for dialog parenting

    // ── Public API ────────────────────────────────────────────────────────────

    /** Builds and returns the History tab panel. */
    public JPanel build() {
        panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(6, 0, 0, 0));

        panel.add(buildFilterRow(), BorderLayout.NORTH);
        panel.add(buildSplitPane(), BorderLayout.CENTER);
        panel.add(buildStatsBar(),  BorderLayout.SOUTH);
        return panel;
    }

    /** Reloads trade history from disk and refreshes the table. */
    public void refresh() {
        refreshHistoryList();
    }

    // ── Private — layout ─────────────────────────────────────────────────────

    private JPanel buildFilterRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));

        row.add(new JLabel("Search:"));
        historySearchField = new JTextField(20);
        historySearchField.setToolTipText("Filter by customer, date, or receipt body text");
        historySearchField.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { applyHistoryFilter(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { applyHistoryFilter(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) {}
                });
        row.add(historySearchField);

        row.add(new JLabel("  Payment:"));
        historyPaymentCombo = new JComboBox<>(new String[]{"All", "Credit", "Check", "Partial", "Inventory"});
        historyPaymentCombo.addActionListener(e -> applyHistoryFilter());
        row.add(historyPaymentCombo);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> refreshHistoryList());
        row.add(refreshBtn);

        historyStatusLabel = new JLabel("Loading\u2026");
        historyStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        row.add(historyStatusLabel);

        return row;
    }

    private JSplitPane buildSplitPane() {
        // Left: trade list
        String[] cols = {"Date", "Customer", "Payment Type", "Total Value", "# Cards"};
        historyTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        historyTable = new JTable(historyTableModel);
        historyTable.setFont(historyTable.getFont().deriveFont(13f));
        historyTable.setRowHeight(26);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.setAutoCreateRowSorter(true);
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(170);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadHistoryPreview();
        });

        JScrollPane listScroll = new JScrollPane(historyTable);
        listScroll.setBorder(BorderFactory.createTitledBorder("Trade Receipts"));

        // Right: preview + buttons
        historyPreviewArea = new JTextArea();
        historyPreviewArea.setEditable(false);
        historyPreviewArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        historyPreviewArea.setLineWrap(false);
        JScrollPane previewScroll = new JScrollPane(historyPreviewArea);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Receipt Preview"));

        JButton printBtn = new JButton("Print");
        printBtn.setFocusPainted(false);
        printBtn.setPreferredSize(new Dimension(100, 32));
        printBtn.addActionListener(e -> historyPrint());

        JButton pdfBtn = new JButton("Save as PDF");
        pdfBtn.setFocusPainted(false);
        pdfBtn.setPreferredSize(new Dimension(120, 32));
        pdfBtn.addActionListener(e -> historySaveAsPdf());

        JButton explorerBtn = new JButton("Open in Explorer");
        explorerBtn.setFocusPainted(false);
        explorerBtn.setPreferredSize(new Dimension(140, 32));
        explorerBtn.addActionListener(e -> historyOpenInExplorer());

        JPanel previewBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        previewBtns.add(printBtn);
        previewBtns.add(pdfBtn);
        previewBtns.add(explorerBtn);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(previewScroll, BorderLayout.CENTER);
        rightPanel.add(previewBtns,   BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, rightPanel);
        split.setDividerLocation(420);
        split.setResizeWeight(0.45);
        return split;
    }

    private JPanel buildStatsBar() {
        historyStatsTotalTrades = new JLabel("Trades: 0");
        historyStatsTotalCards  = new JLabel("  Cards: 0");
        historyStatsTotalValue  = new JLabel("  Total: $0.00");
        historyStatsAvgValue    = new JLabel("  Avg: $0.00");

        Font statsFont = historyStatsTotalTrades.getFont().deriveFont(12f);
        Color statsFg  = UIManager.getColor("Label.disabledForeground");
        for (JLabel lbl : new JLabel[]{
                historyStatsTotalTrades, historyStatsTotalCards,
                historyStatsTotalValue, historyStatsAvgValue}) {
            lbl.setFont(statsFont);
            lbl.setForeground(statsFg);
        }

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                UIManager.getColor("Separator.foreground")));
        bar.add(historyStatsTotalTrades);
        bar.add(historyStatsTotalCards);
        bar.add(historyStatsTotalValue);
        bar.add(historyStatsAvgValue);
        return bar;
    }

    // ── Private — data ────────────────────────────────────────────────────────

    private void refreshHistoryList() {
        allRecords = TradeHistoryService.loadAll(AppDataDirectory.tradesPath());
        contentCache.clear();
        historyTableModel.setRowCount(0);
        applyHistoryFilter();
    }

    private void applyHistoryFilter() {
        String filter = historySearchField == null ? "" : historySearchField.getText().trim().toLowerCase();
        String paymentFilter = historyPaymentCombo == null
                ? "All" : (String) historyPaymentCombo.getSelectedItem();

        historyTableModel.setRowCount(0);
        int shown = 0;
        for (TradeRecord r : allRecords) {
            if (!"All".equals(paymentFilter)
                    && !r.paymentMethod.toLowerCase().contains(paymentFilter.toLowerCase())) continue;

            if (!filter.isEmpty()) {
                boolean nameMatch = r.customerName.toLowerCase().contains(filter);
                boolean dateMatch = r.date.format(HISTORY_DATE_FMT).toLowerCase().contains(filter);
                boolean bodyMatch = !nameMatch && !dateMatch
                        && getOrLoadContent(r.filename).toLowerCase().contains(filter);
                if (!nameMatch && !dateMatch && !bodyMatch) continue;
            }

            historyTableModel.addRow(new Object[]{
                    r.date.format(HISTORY_DATE_FMT),
                    r.customerName,
                    r.paymentMethod,
                    String.format("$%.2f", r.totalValue),
                    r.totalCards
            });
            shown++;
        }
        if (historyStatusLabel != null) historyStatusLabel.setText(shown + " record(s) found");
        updateHistoryStats();
    }

    private String getOrLoadContent(String filename) {
        return contentCache.computeIfAbsent(filename, path -> {
            try { return Files.readString(Path.of(path), StandardCharsets.UTF_8); }
            catch (IOException e) { return ""; }
        });
    }

    private void updateHistoryStats() {
        if (historyStatsTotalTrades == null) return;
        int tradeCount = historyTableModel.getRowCount();
        int cardCount  = 0;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (int i = 0; i < tradeCount; i++) {
            Object cardsObj = historyTableModel.getValueAt(i, 4);
            try { cardCount += Integer.parseInt(cardsObj.toString()); } catch (Exception ignored) {}
            String valStr = historyTableModel.getValueAt(i, 3).toString()
                    .replace("$", "").replace(",", "").trim();
            try { totalValue = totalValue.add(new BigDecimal(valStr)); } catch (Exception ignored) {}
        }

        BigDecimal avg = tradeCount > 0
                ? totalValue.divide(BigDecimal.valueOf(tradeCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        historyStatsTotalTrades.setText("Trades: " + tradeCount);
        historyStatsTotalCards.setText("  Cards: " + cardCount);
        historyStatsTotalValue.setText(String.format("  Total: $%.2f", totalValue));
        historyStatsAvgValue.setText(String.format("  Avg: $%.2f", avg));
    }

    private TradeRecord selectedRecord() {
        int viewRow = historyTable.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = historyTable.convertRowIndexToModel(viewRow);
        String dateStr   = (String) historyTableModel.getValueAt(modelRow, 0);
        String customer  = (String) historyTableModel.getValueAt(modelRow, 1);
        for (TradeRecord r : allRecords) {
            if (r.date.format(HISTORY_DATE_FMT).equals(dateStr) && r.customerName.equals(customer)) return r;
        }
        return null;
    }

    private void loadHistoryPreview() {
        TradeRecord r = selectedRecord();
        if (r == null) { historyPreviewArea.setText(""); return; }
        try {
            String content = Files.readString(Path.of(r.filename), StandardCharsets.UTF_8);
            historyPreviewArea.setText(content);
            historyPreviewArea.setCaretPosition(0);
        } catch (IOException e) {
            historyPreviewArea.setText("Could not load file: " + e.getMessage());
        }
    }

    private void historyPrint() {
        TradeRecord r = selectedRecord();
        if (r == null) {
            JOptionPane.showMessageDialog(panel, "Please select a record.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            ReceiptPrintService.printReceipt(panel, Files.readString(Path.of(r.filename), StandardCharsets.UTF_8));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(panel, "Could not read file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void historySaveAsPdf() {
        TradeRecord r = selectedRecord();
        if (r == null) {
            JOptionPane.showMessageDialog(panel, "Please select a record.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            String content = Files.readString(Path.of(r.filename), StandardCharsets.UTF_8);
            ReceiptPrintService.saveAsPdf(panel, content, r.filename.replace(".txt", ".pdf"));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(panel, "Could not read file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void historyOpenInExplorer() {
        TradeRecord r = selectedRecord();
        if (r == null) {
            JOptionPane.showMessageDialog(panel, "Please select a record.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(r.filename).getParentFile());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(panel, "Could not open folder: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
