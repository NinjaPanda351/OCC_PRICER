package com.cardpricer.gui.panel;

import com.cardpricer.gui.ShortcutHelpDialog;
import com.cardpricer.model.TradeRecord;
import com.cardpricer.service.ReceiptPrintService;
import com.cardpricer.service.TradeHistoryService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel for browsing, downloading, printing, and deleting generated data files.
 * Contains three tabs: Local Files, Shared Files (configured via Preferences), and
 * History (trade receipts with preview and print/PDF support).
 */
public class FileManagerPanel extends JPanel {

    private static final String   HELP_TITLE = "File Manager — Help";
    private static final String[] HELP_COLS  = {"Tab / Feature", "Description"};
    private static final String[][] HELP_ROWS = {
        {"--- Tabs", ""},
        {"Local Files",     "Files saved by this app on this machine"},
        {"Shared Files",    "Files in the shared network folder (set in Preferences)"},
        {"History",         "Browse and preview past trade receipts"},
        {"--- Local / Shared", ""},
        {"Filter",          "Narrow the file list by category"},
        {"Refresh",         "Reload the file list from disk"},
        {"Open",            "Open the file with your default application"},
        {"Copy to Local",   "Copy a shared file into local storage"},
        {"Delete",          "Permanently remove the selected file"},
        {"--- History", ""},
        {"Search",          "Filter trade history by customer or date"},
        {"Print",           "Send the selected trade receipt to a printer"},
        {"Save PDF",        "Export the selected trade receipt as a PDF"},
        {"Open in Explorer","Reveal the receipt file in Windows Explorer"},
    };

    // Local files tab
    private JTable fileTable;
    private DefaultTableModel tableModel;
    private JComboBox<String> categoryCombo;
    private JLabel statusLabel;

    // Shared files tab
    private JTable sharedTable;
    private DefaultTableModel sharedTableModel;
    private JLabel sharedStatusLabel;

    // History tab
    private JTable historyTable;
    private DefaultTableModel historyTableModel;
    private JTextArea historyPreviewArea;
    private JTextField historySearchField;
    private JComboBox<String> historyPaymentCombo;   // F12
    private JLabel historyStatusLabel;
    private List<TradeRecord> allRecords = new ArrayList<>();

    // F11: Content cache for full-text search
    private final Map<String, String> contentCache = new HashMap<>();

    // F13: Stats bar labels
    private JLabel historyStatsTotalTrades;
    private JLabel historyStatsTotalCards;
    private JLabel historyStatsTotalValue;
    private JLabel historyStatsAvgValue;

    private static final DateTimeFormatter HISTORY_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String[] CATEGORIES = {
            "All Files",
            "Trades",
            "Set Pricer",
            "Inventory",
            "Combined Files"
    };

    /** Constructs the File Manager panel and loads the local file list immediately. */
    public FileManagerPanel() {
        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(createTopPanel(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Local Files",  createLocalTab());
        tabs.addTab("Shared Files", createSharedTab());
        tabs.addTab("History",      createHistoryTab());

        tabs.addChangeListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 1) refreshSharedFileList();
            if (idx == 2) refreshHistoryList();
        });
        add(tabs, BorderLayout.CENTER);

        // Load local files on startup
        refreshFileList();
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Title section
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("File Manager");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Download and manage generated files from trades, set pricer, and inventory");
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

        JPanel titleRow = new JPanel(new BorderLayout(10, 0));
        titleRow.add(titlePanel, BorderLayout.CENTER);
        titleRow.add(helpBtn,    BorderLayout.EAST);

        panel.add(titleRow, BorderLayout.NORTH);

        // Filter section
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        filterPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Filter"),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel filterLabel = new JLabel("Category:");
        categoryCombo = new JComboBox<>(CATEGORIES);
        categoryCombo.setPreferredSize(new Dimension(180, 32));
        categoryCombo.addActionListener(e -> refreshFileList());

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setFocusPainted(false);
        refreshButton.setPreferredSize(new Dimension(100, 32));
        refreshButton.addActionListener(e -> refreshFileList());

        filterPanel.add(filterLabel);
        filterPanel.add(categoryCombo);
        filterPanel.add(refreshButton);

        panel.add(filterPanel, BorderLayout.CENTER);

        // Status section
        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(statusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLocalTab() {
        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.add(createTablePanel(), BorderLayout.CENTER);
        tab.add(createBottomPanel(), BorderLayout.SOUTH);
        return tab;
    }

    // ── Shared Files Tab ─────────────────────────────────────────────────────

    private JPanel createSharedTab() {
        JPanel tab = new JPanel(new BorderLayout(10, 10));

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        sharedStatusLabel = new JLabel("Configure a shared folder in Preferences → Network");
        sharedStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> refreshSharedFileList());
        header.add(sharedStatusLabel);
        header.add(refreshBtn);

        // Table
        String[] cols = {"Filename", "Type", "Size", "Date Modified", "Path"};
        sharedTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        sharedTable = new JTable(sharedTableModel);
        sharedTable.setFont(sharedTable.getFont().deriveFont(14f));
        sharedTable.setRowHeight(28);
        sharedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sharedTable.setAutoCreateRowSorter(true);
        sharedTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        sharedTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        sharedTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        sharedTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        sharedTable.getColumnModel().getColumn(4).setPreferredWidth(250);

        JScrollPane scroll = new JScrollPane(sharedTable);
        scroll.setBorder(BorderFactory.createTitledBorder("Shared Trades Folder"));

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton openBtn = new JButton("Open");
        openBtn.setFocusPainted(false);
        openBtn.setPreferredSize(new Dimension(110, 36));
        openBtn.addActionListener(e -> openSharedFile());

        JButton copyBtn = new JButton("Copy to Local");
        copyBtn.setFocusPainted(false);
        copyBtn.setPreferredSize(new Dimension(140, 36));
        copyBtn.addActionListener(e -> copySharedToLocal());

        btnPanel.add(openBtn);
        btnPanel.add(copyBtn);

        tab.add(header, BorderLayout.NORTH);
        tab.add(scroll,  BorderLayout.CENTER);
        tab.add(btnPanel, BorderLayout.SOUTH);
        return tab;
    }

    private void refreshSharedFileList() {
        sharedTableModel.setRowCount(0);
        String path = PreferencesPanel.getSharedTradesFolder();
        if (path == null || path.isBlank()) {
            sharedStatusLabel.setText("No shared folder configured — go to Preferences → Network");
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            sharedStatusLabel.setText("Shared folder not accessible: " + path);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        File[] files = dir.listFiles(File::isFile);
        int count = 0;
        if (files != null) {
            for (File f : files) {
                sharedTableModel.addRow(new Object[]{
                        f.getName(),
                        getFileType(f.getName()),
                        formatFileSize(f.length()),
                        fmt.format(new Date(f.lastModified())),
                        f.getAbsolutePath()
                });
                count++;
            }
        }
        sharedStatusLabel.setText("Shared folder: " + path + "  (" + count + " file(s))");
    }

    private void openSharedFile() {
        int row = sharedTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a file.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String filePath = (String) sharedTableModel.getValueAt(row, 4);
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(filePath));
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to open file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copySharedToLocal() {
        int row = sharedTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a file.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String srcPath = (String) sharedTableModel.getValueAt(row, 4);
        String filename = (String) sharedTableModel.getValueAt(row, 0);
        File src  = new File(srcPath);
        File localDir = com.cardpricer.util.AppDataDirectory.trades();
        if (!localDir.exists()) localDir.mkdirs();
        File dest = new File(localDir, filename);
        try {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(this,
                    "Copied to local folder:\n" + dest.getAbsolutePath(),
                    "Copy Complete", JOptionPane.INFORMATION_MESSAGE);
            refreshFileList(); // refresh local tab
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to copy file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Local Files Tab ───────────────────────────────────────────────────────

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Table columns: Filename, Type, Size, Date Modified
        String[] columns = {"Filename", "Type", "Size", "Date Modified", "Path"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // All cells non-editable
            }
        };

        fileTable = new JTable(tableModel);
        fileTable.setFont(fileTable.getFont().deriveFont(14f));
        fileTable.setRowHeight(28);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Column widths
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(300); // Filename
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Type
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Size
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(150); // Date
        fileTable.getColumnModel().getColumn(4).setPreferredWidth(250); // Path

        // Enable table sorting
        fileTable.setAutoCreateRowSorter(true);

        JScrollPane scrollPane = new JScrollPane(fileTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Generated Files"));

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Left: Info
        JLabel infoLabel = new JLabel("Select files and click 'Download' to save to your chosen location");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(infoLabel, BorderLayout.WEST);

        // Right: Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        JButton downloadButton = new JButton("Download Selected");
        downloadButton.setFocusPainted(false);
        downloadButton.setPreferredSize(new Dimension(160, 36));
        downloadButton.addActionListener(e -> downloadSelected());

        JButton openFolderButton = new JButton("Open in File Explorer");
        openFolderButton.setFocusPainted(false);
        openFolderButton.setPreferredSize(new Dimension(180, 36));
        openFolderButton.addActionListener(e -> openSelectedFolder());

        JButton deleteButton = new JButton("Delete Selected");
        deleteButton.setFocusPainted(false);
        deleteButton.setPreferredSize(new Dimension(140, 36));
        deleteButton.setForeground(new Color(180, 0, 0));
        deleteButton.addActionListener(e -> deleteSelected());

        buttonPanel.add(openFolderButton);
        buttonPanel.add(downloadButton);
        buttonPanel.add(deleteButton);

        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    private void refreshFileList() {
        tableModel.setRowCount(0);

        String selectedCategory = (String) categoryCombo.getSelectedItem();
        List<File> files = new ArrayList<>();

        // Collect files based on category
        if ("All Files".equals(selectedCategory)) {
            files.addAll(getFilesFromDirectory(com.cardpricer.util.AppDataDirectory.tradesPath()));
            files.addAll(getFilesFromDirectory(com.cardpricer.util.AppDataDirectory.pricesPath()));
            files.addAll(getFilesFromDirectory(com.cardpricer.util.AppDataDirectory.combinedFilesPath()));
            files.addAll(getFilesFromDirectory(com.cardpricer.util.AppDataDirectory.inventoryPath()));
        } else if ("Trades".equals(selectedCategory)) {
            files.addAll(getFilesFromDirectory(com.cardpricer.util.AppDataDirectory.tradesPath()));
        } else if ("Set Pricer".equals(selectedCategory)) {
            files.addAll(getFilesFromDirectory(com.cardpricer.util.AppDataDirectory.pricesPath()));
        } else if ("Inventory".equals(selectedCategory)) {
            files.addAll(getFilesFromDirectory(com.cardpricer.util.AppDataDirectory.inventoryPath()));
        } else if ("Combined Files".equals(selectedCategory)) {
            files.addAll(getFilesFromDirectory(com.cardpricer.util.AppDataDirectory.combinedFilesPath()));
        }

        // Populate table
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (File file : files) {
            if (file.isFile()) {
                String filename = file.getName();
                String type = getFileType(filename);
                String size = formatFileSize(file.length());
                String dateModified = dateFormat.format(new Date(file.lastModified()));
                String path = file.getAbsolutePath();

                tableModel.addRow(new Object[]{filename, type, size, dateModified, path});
            }
        }

        statusLabel.setText(String.format("Found %d file(s)", files.size()));
    }

    private List<File> getFilesFromDirectory(String dirPath) {
        List<File> files = new ArrayList<>();
        File dir = new File(dirPath);

        if (dir.exists() && dir.isDirectory()) {
            File[] fileArray = dir.listFiles();
            if (fileArray != null) {
                files.addAll(Arrays.asList(fileArray));
            }
        }

        return files;
    }

    private String getFileType(String filename) {
        if (filename.endsWith(".csv")) return "CSV";
        if (filename.endsWith(".txt")) return "Text";
        if (filename.endsWith(".pdf")) return "PDF";
        return "Other";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ── History Tab ───────────────────────────────────────────────────────────

    private JPanel createHistoryTab() {
        JPanel tab = new JPanel(new BorderLayout(8, 8));
        tab.setBorder(new EmptyBorder(6, 0, 0, 0));

        // ── Top: filter row ──────────────────────────────────────────────────
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterRow.add(new JLabel("Search:"));
        historySearchField = new JTextField(20);
        historySearchField.setToolTipText("Filter by customer, date, or receipt body text");
        historySearchField.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { applyHistoryFilter(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { applyHistoryFilter(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) {}
                });
        filterRow.add(historySearchField);

        // F12: Payment type filter
        filterRow.add(new JLabel("  Payment:"));
        historyPaymentCombo = new JComboBox<>(new String[]{"All", "Credit", "Check", "Partial", "Inventory"});
        historyPaymentCombo.addActionListener(e -> applyHistoryFilter());
        filterRow.add(historyPaymentCombo);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> refreshHistoryList());
        filterRow.add(refreshBtn);

        historyStatusLabel = new JLabel("Loading…");
        historyStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        filterRow.add(historyStatusLabel);

        tab.add(filterRow, BorderLayout.NORTH);

        // ── Left pane: trade list ────────────────────────────────────────────
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

        // When a row is selected, load the file into the preview pane
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadHistoryPreview();
        });

        JScrollPane listScroll = new JScrollPane(historyTable);
        listScroll.setBorder(BorderFactory.createTitledBorder("Trade Receipts"));

        // ── Right pane: preview ──────────────────────────────────────────────
        historyPreviewArea = new JTextArea();
        historyPreviewArea.setEditable(false);
        historyPreviewArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        historyPreviewArea.setLineWrap(false);
        JScrollPane previewScroll = new JScrollPane(historyPreviewArea);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Receipt Preview"));

        // Action buttons below preview
        JButton histPrintBtn = new JButton("Print");
        histPrintBtn.setFocusPainted(false);
        histPrintBtn.setPreferredSize(new Dimension(100, 32));
        histPrintBtn.addActionListener(e -> historyPrint());

        JButton histPdfBtn = new JButton("Save as PDF");
        histPdfBtn.setFocusPainted(false);
        histPdfBtn.setPreferredSize(new Dimension(120, 32));
        histPdfBtn.addActionListener(e -> historySaveAsPdf());

        JButton histOpenBtn = new JButton("Open in Explorer");
        histOpenBtn.setFocusPainted(false);
        histOpenBtn.setPreferredSize(new Dimension(140, 32));
        histOpenBtn.addActionListener(e -> historyOpenInExplorer());

        JPanel previewBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        previewBtns.add(histPrintBtn);
        previewBtns.add(histPdfBtn);
        previewBtns.add(histOpenBtn);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(previewScroll, BorderLayout.CENTER);
        rightPanel.add(previewBtns,  BorderLayout.SOUTH);

        // ── Split pane ───────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, rightPanel);
        split.setDividerLocation(420);
        split.setResizeWeight(0.45);
        tab.add(split, BorderLayout.CENTER);

        // ── F13: Stats bar ────────────────────────────────────────────────────
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

        JPanel statsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        statsBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                UIManager.getColor("Separator.foreground")));
        statsBar.add(historyStatsTotalTrades);
        statsBar.add(historyStatsTotalCards);
        statsBar.add(historyStatsTotalValue);
        statsBar.add(historyStatsAvgValue);
        tab.add(statsBar, BorderLayout.SOUTH);

        return tab;
    }

    private void refreshHistoryList() {
        allRecords = TradeHistoryService.loadAll(com.cardpricer.util.AppDataDirectory.tradesPath());
        contentCache.clear(); // F11: invalidate content cache when records reload
        historyTableModel.setRowCount(0);
        applyHistoryFilter();
    }

    private void applyHistoryFilter() {
        String filter = historySearchField == null ? "" : historySearchField.getText().trim().toLowerCase();

        // F12: Payment filter
        String paymentFilter = (historyPaymentCombo == null)
                ? "All" : (String) historyPaymentCombo.getSelectedItem();

        historyTableModel.setRowCount(0);
        int shown = 0;
        for (TradeRecord r : allRecords) {
            // F12: Apply payment method filter first
            if (!"All".equals(paymentFilter)
                    && !r.paymentMethod.toLowerCase().contains(paymentFilter.toLowerCase())) {
                continue;
            }

            // Text filter: check customer name, then date string, then full file content (F11)
            if (!filter.isEmpty()) {
                boolean nameMatch  = r.customerName.toLowerCase().contains(filter);
                boolean dateMatch  = r.date.format(HISTORY_DATE_FMT).toLowerCase().contains(filter);
                boolean bodyMatch  = !nameMatch && !dateMatch
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
        if (historyStatusLabel != null) {
            historyStatusLabel.setText(shown + " record(s) found");
        }
        updateHistoryStats(); // F13
    }

    /** F11: Returns cached file content, loading on first access. */
    private String getOrLoadContent(String filename) {
        return contentCache.computeIfAbsent(filename, path -> {
            try {
                return Files.readString(Path.of(path), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }

    /** F13: Updates the stats bar with counts and values from the current filtered table. */
    private void updateHistoryStats() {
        if (historyStatsTotalTrades == null) return;
        int tradeCount = historyTableModel.getRowCount();
        int cardCount  = 0;
        java.math.BigDecimal totalValue = java.math.BigDecimal.ZERO;

        for (int i = 0; i < tradeCount; i++) {
            Object cardsObj = historyTableModel.getValueAt(i, 4);
            if (cardsObj instanceof Integer) {
                cardCount += (Integer) cardsObj;
            } else {
                try { cardCount += Integer.parseInt(cardsObj.toString()); } catch (Exception ignored) {}
            }
            String valStr = historyTableModel.getValueAt(i, 3).toString()
                    .replace("$", "").replace(",", "").trim();
            try { totalValue = totalValue.add(new java.math.BigDecimal(valStr)); } catch (Exception ignored) {}
        }

        java.math.BigDecimal avgValue = tradeCount > 0
                ? totalValue.divide(java.math.BigDecimal.valueOf(tradeCount), 2,
                    java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;

        historyStatsTotalTrades.setText("Trades: " + tradeCount);
        historyStatsTotalCards.setText("  Cards: " + cardCount);
        historyStatsTotalValue.setText(String.format("  Total: $%.2f", totalValue));
        historyStatsAvgValue.setText(String.format("  Avg: $%.2f", avgValue));
    }

    /** Returns the TradeRecord for the currently selected history row, or null. */
    private TradeRecord selectedRecord() {
        int viewRow = historyTable.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = historyTable.convertRowIndexToModel(viewRow);

        // Find the matching record in allRecords (accounting for the filter)
        String dateStr = (String) historyTableModel.getValueAt(modelRow, 0);
        String customer = (String) historyTableModel.getValueAt(modelRow, 1);
        for (TradeRecord r : allRecords) {
            if (r.date.format(HISTORY_DATE_FMT).equals(dateStr)
                    && r.customerName.equals(customer)) {
                return r;
            }
        }
        return null;
    }

    private void loadHistoryPreview() {
        TradeRecord r = selectedRecord();
        if (r == null) {
            historyPreviewArea.setText("");
            return;
        }
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
            JOptionPane.showMessageDialog(this, "Please select a record.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            String content = Files.readString(Path.of(r.filename), StandardCharsets.UTF_8);
            ReceiptPrintService.printReceipt(this, content);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not read file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void historySaveAsPdf() {
        TradeRecord r = selectedRecord();
        if (r == null) {
            JOptionPane.showMessageDialog(this, "Please select a record.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            String content = Files.readString(Path.of(r.filename), StandardCharsets.UTF_8);
            String pdfPath = r.filename.replace(".txt", ".pdf");
            ReceiptPrintService.saveAsPdf(this, content, pdfPath);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not read file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void historyOpenInExplorer() {
        TradeRecord r = selectedRecord();
        if (r == null) {
            JOptionPane.showMessageDialog(this, "Please select a record.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File dir = new File(r.filename).getParentFile();
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not open folder: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void downloadSelected() {
        int[] selectedRows = fileTable.getSelectedRows();

        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select files to download",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Choose destination folder
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose Download Location");

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File destinationDir = chooser.getSelectedFile();
        int successCount = 0;
        int failCount = 0;

        for (int selectedRow : selectedRows) {
            try {
                String sourcePath = (String) tableModel.getValueAt(selectedRow, 4);
                String filename = (String) tableModel.getValueAt(selectedRow, 0);

                File sourceFile = new File(sourcePath);
                File destFile = new File(destinationDir, filename);

                Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                successCount++;
            } catch (IOException e) {
                failCount++;
                e.printStackTrace();
            }
        }

        String message = String.format("Download complete!\n\n" +
                        "Success: %d file(s)\n" +
                        "Failed: %d file(s)\n\n" +
                        "Location: %s",
                successCount, failCount, destinationDir.getAbsolutePath());

        JOptionPane.showMessageDialog(this,
                message,
                "Download Complete",
                JOptionPane.INFORMATION_MESSAGE);

        statusLabel.setText(String.format("Downloaded %d file(s)", successCount));
    }

    private void openSelectedFolder() {
        int selectedRow = fileTable.getSelectedRow();

        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select a file",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String filePath = (String) tableModel.getValueAt(selectedRow, 4);
        File file = new File(filePath);
        File parentDir = file.getParentFile();

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(parentDir);
            } else {
                // Fallback for systems without Desktop support
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec("explorer " + parentDir.getAbsolutePath());
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec("open " + parentDir.getAbsolutePath());
                } else {
                    Runtime.getRuntime().exec("xdg-open " + parentDir.getAbsolutePath());
                }
            }
            statusLabel.setText("Opened folder: " + parentDir.getName());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open folder: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        int[] selectedRows = fileTable.getSelectedRows();

        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select files to delete",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Delete %d selected file(s)?\n\nThis cannot be undone!",
                        selectedRows.length),
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (int selectedRow : selectedRows) {
            try {
                String filePath = (String) tableModel.getValueAt(selectedRow, 4);
                File file = new File(filePath);

                if (file.delete()) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                e.printStackTrace();
            }
        }

        String message = String.format("Delete complete!\n\n" +
                        "Deleted: %d file(s)\n" +
                        "Failed: %d file(s)",
                successCount, failCount);

        JOptionPane.showMessageDialog(this,
                message,
                "Delete Complete",
                JOptionPane.INFORMATION_MESSAGE);

        // Refresh the file list
        refreshFileList();
    }
}