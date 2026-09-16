package com.cardpricer.gui.panel;

import com.cardpricer.gui.ShortcutHelpDialog;
import com.cardpricer.model.TradeRecord;
import com.cardpricer.util.AppTheme;
import com.cardpricer.service.ReceiptPrintService;
import com.cardpricer.service.TradeHistoryService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
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
        {"Copy file path",  "Copy full paths of selected files or a history receipt to the clipboard"},
        {"Delete",          "Permanently remove the selected file"},
        {"--- History", ""},
        {"Search",          "Filter trade history by customer or date"},
        {"Print",           "Send the selected trade receipt to a printer"},
        {"Save PDF",        "Export the selected trade receipt as a PDF"},
        {"Open in Explorer","Reveal the receipt file in Windows Explorer"},
    };

    // Local files tab
    private JTable fileTable;
    private JTabbedPane tabs;
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
    private JComboBox<String> historyInventoryFilter;
    private JCheckBox inventoriedCheck;
    private JButton editTradeButton;
    private boolean inventorySavePending;
    private JLabel inventorySyncLabel;
    private boolean historyRefreshRunning;
    private long inventorySyncGeneration=-1;
    private final Timer inventoryRefreshTimer=new Timer(2_000,e -> {
        if (!isShowing() || tabs.getSelectedIndex()!=2) return;
        inventorySyncLabel.setText(com.cardpricer.service.InventoryStatusSyncService.status());
        if (inventorySyncGeneration!=com.cardpricer.service.InventoryStatusSyncService.generation()) refreshHistoryList(false);
    });
    private java.util.function.Consumer<com.cardpricer.model.TradeDraft> tradeEditor;
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
        setBorder(new EmptyBorder(16, 20, 14, 20));

        add(createTopPanel(), BorderLayout.NORTH);

        tabs = new JTabbedPane();
        tabs.addTab("Local Files",  createLocalTab());
        tabs.addTab("Shared Files", createSharedTab());
        tabs.addTab("History",      createHistoryTab());

        tabs.addChangeListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 1) refreshSharedFileList();
            if (idx == 2) refreshHistoryList();
        });
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED)!=0
                    && isShowing() && tabs.getSelectedIndex()==2) refreshHistoryList();
        });
        add(tabs, BorderLayout.CENTER);

        // Load local files on startup
        refreshFileList();
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel titlePanel = AppTheme.panelHeader("Files & history",
                "Browse generated files and trade history");

        JButton helpBtn = new JButton("?");


        helpBtn.setFont(helpBtn.getFont().deriveFont(Font.BOLD, 14f));
        helpBtn.setToolTipText("Help");
        helpBtn.addActionListener(e ->
                ShortcutHelpDialog.show(SwingUtilities.getWindowAncestor(this),
                        HELP_TITLE, HELP_COLS, HELP_ROWS));

        JPanel titleRow = new JPanel(new BorderLayout(10, 0));
        titleRow.add(titlePanel, BorderLayout.CENTER);
        JPanel headerActions = AppTheme.transparent(new com.cardpricer.gui.WrapLayout(FlowLayout.RIGHT, 0, 0));
        headerActions.add(helpBtn);
        titleRow.add(headerActions, BorderLayout.EAST);

        panel.add(titleRow, BorderLayout.NORTH);

        // Filter section
        JPanel filterPanel = AppTheme.surface(new com.cardpricer.gui.WrapLayout(FlowLayout.LEFT, 10, 0), 16);

        JLabel filterLabel = new JLabel("Category:");
        categoryCombo = new JComboBox<>(CATEGORIES);
        categoryCombo.setPreferredSize(new Dimension(180, 32));
        categoryCombo.addActionListener(e -> refreshFileList());

        JButton refreshButton = new JButton("Refresh");


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

    /** Allows the dashboard history action to open the requested tab directly. */
    public void showHistory() { if (tabs.getSelectedIndex()==2) refreshHistoryList(); else tabs.setSelectedIndex(2); }

    public void setTradeEditor(java.util.function.Consumer<com.cardpricer.model.TradeDraft> editor) { tradeEditor=editor; }

    @Override public void addNotify() { super.addNotify();inventoryRefreshTimer.start(); }
    @Override public void removeNotify() { inventoryRefreshTimer.stop();super.removeNotify(); }

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
        JPanel header = new JPanel(new com.cardpricer.gui.WrapLayout(FlowLayout.LEFT, 10, 6));
        sharedStatusLabel = new JLabel("Configure a shared folder in Preferences → Network");
        sharedStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        JButton refreshBtn = new JButton("Refresh");

        refreshBtn.addActionListener(e -> refreshSharedFileList());
        header.add(sharedStatusLabel);
        header.add(refreshBtn);

        // Table
        String[] cols = {"Filename", "Type", "Size", "Date Modified", "Path"};
        sharedTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        sharedTable = new com.cardpricer.gui.EmptyStateTable(sharedTableModel, "Your shared files will appear here", "Configure a shared folder in Preferences to connect this workspace.");
        sharedTable.setFont(sharedTable.getFont().deriveFont(14f));
        AppTheme.styleTable(sharedTable);
        sharedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<DefaultTableModel> sharedSorter = new TableRowSorter<>(sharedTableModel);
        sharedSorter.setComparator(2, FileManagerPanel::compareSizeStrings); // Size — numeric
        sharedSorter.setSortKeys(List.of(new RowSorter.SortKey(3, SortOrder.DESCENDING))); // newest first
        sharedTable.setRowSorter(sharedSorter);
        sharedTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        sharedTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        sharedTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        sharedTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        sharedTable.getColumnModel().getColumn(4).setPreferredWidth(250);

        JScrollPane scroll = new com.cardpricer.gui.ResponsiveTableScroll(sharedTable, 200, 90, 80, 140, 180);
        scroll.setColumnHeaderView(sharedTable.getTableHeader()); scroll.setBorder(AppTheme.cardBorder(0));

        // Buttons
        JPanel btnPanel = new JPanel(new com.cardpricer.gui.WrapLayout(FlowLayout.RIGHT, 10, 5));
        JButton openBtn = new JButton("Open");


        openBtn.addActionListener(e -> openSharedFile());

        JButton copyBtn = new JButton("Copy to Local");


        copyBtn.addActionListener(e -> copySharedToLocal());

        btnPanel.add(openBtn);
        btnPanel.add(createCopyPathButton(sharedTable,
                row -> (String) sharedTableModel.getValueAt(row, 4), sharedStatusLabel));
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
        sharedStatusLabel.setText("Connecting to shared folder...");

        // Run all network I/O off the EDT — File.exists()/listFiles() on a UNC path can
        // block for 30+ seconds when the host is on a different network segment.
        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() {
                File dir = new File(path);
                if (!dir.exists() || !dir.isDirectory()) return null;
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                File[] files = dir.listFiles(File::isFile);
                List<Object[]> rows = new ArrayList<>();
                if (files != null) {
                    for (File f : files) {
                        rows.add(new Object[]{
                                f.getName(),
                                getFileType(f.getName()),
                                formatFileSize(f.length()),
                                fmt.format(new Date(f.lastModified())),
                                f.getAbsolutePath()
                        });
                    }
                }
                return rows;
            }
            @Override
            protected void done() {
                List<Object[]> rows;
                try { rows = get(); } catch (Exception ex) { rows = null; }
                if (rows == null) {
                    sharedStatusLabel.setText("Shared folder not accessible: " + path);
                    return;
                }
                for (Object[] row : rows) sharedTableModel.addRow(row);
                sharedStatusLabel.setText("Shared folder: " + path + "  (" + rows.size() + " file(s))");
            }
        }.execute();
    }

    private void openSharedFile() {
        int row = sharedTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a file.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String filePath = (String) sharedTableModel.getValueAt(sharedTable.convertRowIndexToModel(row), 4);
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
        int modelRow = sharedTable.convertRowIndexToModel(row);
        String srcPath = (String) sharedTableModel.getValueAt(modelRow, 4);
        String filename = (String) sharedTableModel.getValueAt(modelRow, 0);
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

        fileTable = new com.cardpricer.gui.EmptyStateTable(tableModel, "A home for your saved files", "Trade receipts, pricing exports, and inventory files appear here.");
        fileTable.setFont(fileTable.getFont().deriveFont(14f));
        AppTheme.styleTable(fileTable);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Column widths
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(300); // Filename
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Type
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Size
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(150); // Date
        fileTable.getColumnModel().getColumn(4).setPreferredWidth(250); // Path

        // Enable table sorting — newest first, numeric size/amount comparators
        TableRowSorter<DefaultTableModel> fileSorter = new TableRowSorter<>(tableModel);
        fileSorter.setComparator(2, FileManagerPanel::compareSizeStrings); // Size — numeric
        fileSorter.setSortKeys(List.of(new RowSorter.SortKey(3, SortOrder.DESCENDING))); // newest first
        fileTable.setRowSorter(fileSorter);

        JScrollPane scrollPane = new com.cardpricer.gui.ResponsiveTableScroll(fileTable, 200, 90, 80, 140, 180);
        scrollPane.setColumnHeaderView(fileTable.getTableHeader()); scrollPane.setBorder(AppTheme.cardBorder(0));

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Left: Info
        JLabel infoLabel = new JLabel("Select files to download or delete.");
        infoLabel.setToolTipText("Select files, then choose Download Selected to save them to a folder.");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(infoLabel, BorderLayout.CENTER);

        // Right: Action buttons
        JPanel buttonPanel = new JPanel(new com.cardpricer.gui.WrapLayout(FlowLayout.RIGHT, 10, 5));

        JButton downloadButton = new JButton("Download Selected");


        downloadButton.addActionListener(e -> downloadSelected());

        JButton openFolderButton = new JButton("Open in File Explorer");


        openFolderButton.addActionListener(e -> openSelectedFolder());

        JButton deleteButton = AppTheme.dangerButton("Delete Selected");

        deleteButton.addActionListener(e -> deleteSelected());

        buttonPanel.add(openFolderButton);
        buttonPanel.add(createCopyPathButton(fileTable,
                row -> (String) tableModel.getValueAt(row, 4), statusLabel));
        buttonPanel.add(downloadButton);
        buttonPanel.add(deleteButton);

        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    private JButton createCopyPathButton(JTable table, java.util.function.IntFunction<String> pathAtRow,
                                         JLabel feedback) {
        JButton button = new JButton("Copy file path");
        button.setToolTipText("Copy the full path; multiple selected paths are copied on separate lines.");
        button.setEnabled(table.getSelectedRowCount() > 0);
        table.getSelectionModel().addListSelectionListener(e ->
                button.setEnabled(table.getSelectedRowCount() > 0));
        button.addActionListener(e -> {
            int[] selectedRows = table.getSelectedRows();
            if (selectedRows.length == 0) return;
            try {
                List<String> paths = new ArrayList<>();
                for (int row : selectedRows) {
                    String path = pathAtRow.apply(table.convertRowIndexToModel(row));
                    paths.add(Path.of(path).toAbsolutePath().normalize().toString());
                }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(String.join(System.lineSeparator(), paths)), null);
                feedback.setText(paths.size() == 1 ? "File path copied" : paths.size() + " file paths copied");
            } catch (IllegalStateException | HeadlessException | SecurityException ex) {
                JOptionPane.showMessageDialog(this, "Could not copy the file path. Please try again.",
                        "Clipboard unavailable", JOptionPane.ERROR_MESSAGE);
            }
        });
        return button;
    }

    private void refreshFileList() {
        tableModel.setRowCount(0);
        statusLabel.setText("Loading files…");
        final String selectedCategory = (String) categoryCombo.getSelectedItem();

        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() {
                List<File> files = new ArrayList<>();
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
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                List<Object[]> rows = new ArrayList<>();
                for (File file : files) {
                    if (file.isFile()) {
                        rows.add(new Object[]{
                                file.getName(), getFileType(file.getName()),
                                formatFileSize(file.length()),
                                dateFormat.format(new Date(file.lastModified())),
                                file.getAbsolutePath()
                        });
                    }
                }
                return rows;
            }
            @Override
            protected void done() {
                try {
                    List<Object[]> rows = get();
                    for (Object[] row : rows) tableModel.addRow(row);
                    statusLabel.setText(String.format(java.util.Locale.ROOT, "Found %d file(s)", rows.size()));
                } catch (Exception ex) {
                    statusLabel.setText("Failed to load files");
                }
            }
        }.execute();
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
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Parses a size string produced by {@link #formatFileSize} back to bytes for numeric sorting. */
    private static long parseSizeBytes(String s) {
        try {
            if (s.endsWith(" B"))  return Long.parseLong(s.replace(" B", "").trim());
            if (s.endsWith(" KB")) return (long)(Double.parseDouble(s.replace(" KB", "").trim()) * 1024);
            if (s.endsWith(" MB")) return (long)(Double.parseDouble(s.replace(" MB", "").trim()) * 1024 * 1024);
        } catch (Exception ignored) {}
        return 0;
    }

    /** Comparator for size strings: numeric order rather than lexicographic. */
    private static int compareSizeStrings(Object a, Object b) {
        return Long.compare(parseSizeBytes(a.toString()), parseSizeBytes(b.toString()));
    }

    // ── History Tab ───────────────────────────────────────────────────────────

    private JPanel createHistoryTab() {
        JPanel tab = new JPanel(new BorderLayout(8, 8));
        tab.setBorder(new EmptyBorder(6, 0, 0, 0));

        // ── Top: filter row ──────────────────────────────────────────────────
        JPanel filterRow = new JPanel(new com.cardpricer.gui.WrapLayout(FlowLayout.LEFT, 10, 4));
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
        historyInventoryFilter=new JComboBox<>(new String[]{"All POS statuses", "Needs inventory", "Inventoried"});
        historyInventoryFilter.addActionListener(e -> applyHistoryFilter());
        filterRow.add(historyInventoryFilter);

        JButton refreshBtn = new JButton("Refresh");

        refreshBtn.addActionListener(e -> refreshHistoryList());
        filterRow.add(refreshBtn);

        historyStatusLabel = new JLabel("Loading…");
        historyStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        filterRow.add(historyStatusLabel);

        tab.add(filterRow, BorderLayout.NORTH);

        // ── Left pane: trade list ────────────────────────────────────────────
        String[] cols = {"Date", "Customer", "Payment Type", "Total Value", "# Cards", "In POS"};
        historyTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c==5 ? Boolean.class : super.getColumnClass(c); }
        };
        historyTable = new JTable(historyTableModel);
        historyTable.setFont(historyTable.getFont().deriveFont(13f));
        historyTable.setRowHeight(26);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<DefaultTableModel> historySorter = new TableRowSorter<>(historyTableModel);
        historySorter.setComparator(3, (a, b) -> {   // Total Value — numeric
            try {
                double va = Double.parseDouble(a.toString().replace("$", "").replace(",", "").trim());
                double vb = Double.parseDouble(b.toString().replace("$", "").replace(",", "").trim());
                return Double.compare(va, vb);
            } catch (Exception ignored) { return a.toString().compareTo(b.toString()); }
        });
        historySorter.setComparator(4, (a, b) -> {   // # Cards — numeric
            try { return Integer.compare(Integer.parseInt(a.toString()), Integer.parseInt(b.toString())); }
            catch (Exception ignored) { return a.toString().compareTo(b.toString()); }
        });
        historySorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.DESCENDING))); // newest first
        historyTable.setRowSorter(historySorter);
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(170);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(60);

        // When a row is selected, load the file into the preview pane
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadHistoryPreview();
        });

        JScrollPane listScroll = new com.cardpricer.gui.ResponsiveTableScroll(historyTable, 130, 140, 130, 100, 70, 65);
        listScroll.setColumnHeaderView(historyTable.getTableHeader()); listScroll.setBorder(AppTheme.cardBorder(0));

        // ── Right pane: preview ──────────────────────────────────────────────
        historyPreviewArea = new JTextArea();
        historyPreviewArea.setEditable(false);
        historyPreviewArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        historyPreviewArea.setLineWrap(false);
        JScrollPane previewScroll = new JScrollPane(historyPreviewArea);
        previewScroll.setBorder(AppTheme.sectionBorder("Receipt preview"));

        // Action buttons below preview
        JButton histPrintBtn = new JButton("Print");


        histPrintBtn.addActionListener(e -> historyPrint());

        JButton histPdfBtn = new JButton("Save as PDF");


        histPdfBtn.addActionListener(e -> historySaveAsPdf());

        JButton histOpenBtn = new JButton("Open in Explorer");


        histOpenBtn.addActionListener(e -> historyOpenInExplorer());

        JPanel previewBtns = new JPanel(new com.cardpricer.gui.WrapLayout(FlowLayout.RIGHT, 8, 4));
        editTradeButton=new JButton("Edit trade");
        editTradeButton.setEnabled(false);
        editTradeButton.addActionListener(e -> editHistoryTrade());
        previewBtns.add(editTradeButton);
        previewBtns.add(histPrintBtn);
        previewBtns.add(histPdfBtn);
        previewBtns.add(histOpenBtn);
        previewBtns.add(createCopyPathButton(historyTable,
                row -> visibleRecords.get(row).filename, historyStatusLabel));

        JPanel rightPanel = new JPanel(new BorderLayout());
        inventoriedCheck=new JCheckBox("Inventoried into POS");
        inventoriedCheck.setEnabled(false);
        inventoriedCheck.setToolTipText("Shared across computers using the same shared trades folder. Corrections need POS review again.");
        inventoriedCheck.addActionListener(e -> saveInventoryStatus());
        JPanel inventoryStatus=new JPanel(new BorderLayout(0,4));
        inventoryStatus.add(inventoriedCheck,BorderLayout.NORTH);
        inventorySyncLabel=AppTheme.mutedLabel(com.cardpricer.service.InventoryStatusSyncService.status());
        inventoryStatus.add(inventorySyncLabel,BorderLayout.SOUTH);
        rightPanel.add(inventoryStatus,BorderLayout.NORTH);
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

        JPanel statsBar = new JPanel(new com.cardpricer.gui.WrapLayout(FlowLayout.LEFT, 4, 4));
        statsBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                UIManager.getColor("Separator.foreground")));
        statsBar.add(historyStatsTotalTrades);
        statsBar.add(historyStatsTotalCards);
        statsBar.add(historyStatsTotalValue);
        statsBar.add(historyStatsAvgValue);
        tab.add(statsBar, BorderLayout.SOUTH);

        return tab;
    }

    private long historyGeneration;

    private void refreshHistoryList() { refreshHistoryList(true); }

    private void refreshHistoryList(boolean requestSync) {
        if (historyRefreshRunning || inventorySavePending) return;
        historyRefreshRunning=true;
        if (requestSync) com.cardpricer.service.InventoryStatusSyncService.requestSync();
        long syncGeneration=com.cardpricer.service.InventoryStatusSyncService.generation();
        long generation=++historyGeneration;
        historyStatusLabel.setText("Loading...");
        java.util.Map<String,String> loadedContent=new java.util.HashMap<>();
        new SwingWorker<List<com.cardpricer.model.TradeRecord>, Void>() {
            @Override
            protected List<com.cardpricer.model.TradeRecord> doInBackground() {
                var records=TradeHistoryService.loadAll(com.cardpricer.util.AppDataDirectory.tradesPath());
                for (var record:records) try { loadedContent.put(record.filename,TradeHistoryService.receiptContent(record,com.cardpricer.util.AppDataDirectory.tradesPath())); }
                catch (Exception failure) { loadedContent.put(record.filename,"Could not load receipt: "+failure.getMessage()); }
                return records;
            }
            @Override
            protected void done() {
                try {
                    if (generation!=historyGeneration) return;
                    TradeRecord selected=selectedRecord();
                    int caret=historyPreviewArea.getCaretPosition();
                    allRecords = get();
                    contentCache.clear(); contentCache.putAll(loadedContent);
                    applyHistoryFilter();
                    if (selected!=null) selectHistoryRecord(selected.historyKey());
                    historyPreviewArea.setCaretPosition(Math.min(caret,historyPreviewArea.getDocument().getLength()));
                    inventorySyncGeneration=syncGeneration;
                } catch (Exception ex) {
                    historyStatusLabel.setText("Could not load history: " + errorMessage(ex));
                } finally { historyRefreshRunning=false; }
            }
        }.execute();
    }

    private final java.util.List<TradeRecord> visibleRecords = new java.util.ArrayList<>();

    private void applyHistoryFilter() {
        String filter = historySearchField == null ? "" : historySearchField.getText().trim().toLowerCase();

        // F12: Payment filter
        String paymentFilter = (historyPaymentCombo == null)
                ? "All" : (String) historyPaymentCombo.getSelectedItem();

        historyTableModel.setRowCount(0);
        visibleRecords.clear();
        int shown = 0;
        for (TradeRecord r : allRecords) {
            int inventoryFilter=historyInventoryFilter==null ? 0 : historyInventoryFilter.getSelectedIndex();
            if ((inventoryFilter==1 && r.inventoried) || (inventoryFilter==2 && !r.inventoried)) continue;
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

            visibleRecords.add(r);
            historyTableModel.addRow(new Object[]{
                    r.date.format(HISTORY_DATE_FMT),
                    r.customerName,
                    r.paymentMethod,
                    String.format(java.util.Locale.ROOT, "$%.2f", r.totalValue),
                    r.totalCards,
                    r.inventoried
            });
            shown++;
        }
        if (historyStatusLabel != null) {
            historyStatusLabel.setText(shown + " record(s) found");
        }
        updateHistoryStats(); // F13
    }

    /** F11: Returns cached file content, loading on first access. */
    private String getOrLoadContent(String filename) { return contentCache.getOrDefault(filename, ""); }

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
        historyStatsTotalValue.setText(String.format(java.util.Locale.ROOT, "  Total: $%.2f", totalValue));
        historyStatsAvgValue.setText(String.format(java.util.Locale.ROOT, "  Avg: $%.2f", avgValue));
    }

    /** Returns the TradeRecord for the currently selected history row, or null. */
    private TradeRecord selectedRecord() {
        int viewRow = historyTable.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = historyTable.convertRowIndexToModel(viewRow);
        return modelRow < visibleRecords.size() ? visibleRecords.get(modelRow) : null;
    }

    private void loadHistoryPreview() {
        TradeRecord record=selectedRecord();
        historyPreviewArea.setText(record==null ? "" : getOrLoadContent(record.filename));
        historyPreviewArea.setCaretPosition(0);
        if (inventoriedCheck!=null) {
            inventoriedCheck.setSelected(record!=null && record.inventoried);
            inventoriedCheck.setEnabled(record!=null && !inventorySavePending);
        }
        if (editTradeButton!=null) editTradeButton.setEnabled(record!=null && !inventorySavePending);
    }

    private void saveInventoryStatus() {
        TradeRecord record=selectedRecord();
        if (record==null || inventorySavePending) return;
        boolean value=inventoriedCheck.isSelected();
        inventorySavePending=true;
        inventoriedCheck.setEnabled(false);
        editTradeButton.setEnabled(false);
        new SwingWorker<Void,Void>() {
            protected Void doInBackground() throws Exception {
                TradeHistoryService.repository(com.cardpricer.util.AppDataDirectory.tradesPath()).setInventoried(record,value);
                return null;
            }
            protected void done() {
                inventorySavePending=false;
                try {
                    get();
                    historyGeneration++;
                    allRecords.replaceAll(r -> r.historyKey().equals(record.historyKey()) && r.revision==record.revision ? r.withInventoried(value) : r);
                    applyHistoryFilter();
                    selectHistoryRecord(record.historyKey());
                    historyStatusLabel.setText(value ? "Marked inventoried into POS" : "Marked as needing POS inventory");
                    inventorySyncLabel.setText("POS status: saved locally; syncing...");
                    com.cardpricer.service.InventoryStatusSyncService.requestSync();
                } catch (Exception failure) {
                    loadHistoryPreview();
                    JOptionPane.showMessageDialog(FileManagerPanel.this,"Could not save POS status: " + errorMessage(failure),"Save failed",JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void selectHistoryRecord(String key) {
        for (int i=0;i<visibleRecords.size();i++) if (visibleRecords.get(i).historyKey().equals(key)) {
            int view=historyTable.convertRowIndexToView(i);
            if (view>=0) historyTable.setRowSelectionInterval(view,view);
            return;
        }
        loadHistoryPreview();
    }

    private void editHistoryTrade() {
        TradeRecord record=selectedRecord();
        if (record==null) return;
        if (record.tradeId==null) { editLegacyReceipt(record); return; }
        editTradeButton.setEnabled(false);
        new SwingWorker<com.cardpricer.model.TradeDraft,Void>() {
            protected com.cardpricer.model.TradeDraft doInBackground() throws Exception {
                var repo=TradeHistoryService.repository(com.cardpricer.util.AppDataDirectory.tradesPath());
                var draft=repo.committedDraft(record.tradeId);
                if (draft==null || draft.revision()!=record.revision)
                    throw new IllegalStateException("Open this trade on the workstation where it was saved, or refresh History if it changed.");
                return draft;
            }
            protected void done() {
                loadHistoryPreview();
                try {
                    var draft=get();
                    if (tradeEditor==null) throw new IllegalStateException("The trade editor is unavailable");
                    tradeEditor.accept(draft);
                } catch (Exception failure) {
                    JOptionPane.showMessageDialog(FileManagerPanel.this,errorMessage(failure),"Could not open trade",JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void editLegacyReceipt(TradeRecord record) {
        JTextArea text=new JTextArea(getOrLoadContent(record.filename),24,70);
        text.setFont(new Font("Monospaced",Font.PLAIN,13));
        JPanel editor=new JPanel(new BorderLayout(8,8));
        editor.add(new JLabel("Older receipt: edit its text below. The original is backed up; POS exports are not recalculated."),BorderLayout.NORTH);
        editor.add(new JScrollPane(text),BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this,editor,"Edit saved receipt",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION) return;
        String content=text.getText();
        new SwingWorker<Void,Void>() {
            protected Void doInBackground() throws Exception {
                TradeHistoryService.saveLegacyReceipt(record,content,com.cardpricer.util.AppDataDirectory.tradesPath());return null;
            }
            protected void done() {
                try { get();refreshHistoryList(); }
                catch (Exception failure) { JOptionPane.showMessageDialog(FileManagerPanel.this,errorMessage(failure),"Could not save receipt",JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }

    private static String errorMessage(Throwable error) {
        while (error.getCause()!=null) error=error.getCause();
        return error.getMessage();
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
                int modelRow = fileTable.convertRowIndexToModel(selectedRow);
                String sourcePath = (String) tableModel.getValueAt(modelRow, 4);
                String filename = (String) tableModel.getValueAt(modelRow, 0);

                File sourceFile = new File(sourcePath);
                File destFile = new File(destinationDir, filename);

                Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                successCount++;
            } catch (IOException e) {
                failCount++;
                e.printStackTrace();
            }
        }

        String message = String.format(java.util.Locale.ROOT, "Download complete!\n\n" +
                        "Success: %d file(s)\n" +
                        "Failed: %d file(s)\n\n" +
                        "Location: %s",
                successCount, failCount, destinationDir.getAbsolutePath());

        JOptionPane.showMessageDialog(this,
                message,
                "Download Complete",
                JOptionPane.INFORMATION_MESSAGE);

        statusLabel.setText(String.format(java.util.Locale.ROOT, "Downloaded %d file(s)", successCount));
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

        String filePath = (String) tableModel.getValueAt(fileTable.convertRowIndexToModel(selectedRow), 4);
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
                String.format(java.util.Locale.ROOT, "Delete %d selected file(s)?\n\nThis cannot be undone!",
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
                String filePath = (String) tableModel.getValueAt(fileTable.convertRowIndexToModel(selectedRow), 4);
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

        String message = String.format(java.util.Locale.ROOT, "Delete complete!\n\n" +
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
