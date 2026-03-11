package com.cardpricer.gui.panel.filemanager;

import com.cardpricer.util.AppDataDirectory;
import com.cardpricer.util.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Local Files tab: browse, download, open, and delete app-generated files.
 */
public final class LocalFilesTab {

    private static final String[] CATEGORIES = {
            "All Files", "Trades", "Set Pricer", "Inventory", "Combined Files"
    };

    private JTable            fileTable;
    private DefaultTableModel tableModel;
    private JComboBox<String> categoryCombo;
    private JLabel            statusLabel;
    private JPanel            panel; // for dialog parenting

    // ── Public API ────────────────────────────────────────────────────────────

    /** Builds and returns the Local Files tab panel (includes its own filter row). */
    public JPanel build() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(4, 0, 0, 0));

        panel.add(buildFilterRow(), BorderLayout.NORTH);
        panel.add(buildTablePanel(), BorderLayout.CENTER);
        panel.add(buildButtonPanel(), BorderLayout.SOUTH);
        return panel;
    }

    /** Reloads the file list from disk using the current category filter. */
    public void refresh() {
        refreshFileList();
    }

    // ── Private — layout ─────────────────────────────────────────────────────

    private JPanel buildFilterRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));

        row.add(new JLabel("Category:"));
        categoryCombo = new JComboBox<>(CATEGORIES);
        categoryCombo.setPreferredSize(new Dimension(180, 32));
        categoryCombo.addActionListener(e -> refreshFileList());
        row.add(categoryCombo);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setFocusPainted(false);
        refreshBtn.setPreferredSize(new Dimension(100, 32));
        refreshBtn.addActionListener(e -> refreshFileList());
        row.add(refreshBtn);

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        row.add(statusLabel);

        return row;
    }

    private JPanel buildTablePanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));

        String[] columns = {"Filename", "Type", "Size", "Date Modified", "Path"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        fileTable = new JTable(tableModel);
        fileTable.setFont(fileTable.getFont().deriveFont(14f));
        fileTable.setRowHeight(28);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setAutoCreateRowSorter(true);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        fileTable.getColumnModel().getColumn(4).setPreferredWidth(250);

        JScrollPane scroll = new JScrollPane(fileTable);
        scroll.setBorder(BorderFactory.createTitledBorder("Generated Files"));
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildButtonPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));

        JLabel infoLabel = new JLabel("Select files and click 'Download' to save to your chosen location");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        p.add(infoLabel, BorderLayout.WEST);

        JButton downloadButton = new JButton("Download Selected");
        downloadButton.setFocusPainted(false);
        downloadButton.setPreferredSize(new Dimension(160, 36));
        downloadButton.addActionListener(e -> downloadSelected());

        JButton openFolderButton = new JButton("Open in File Explorer");
        openFolderButton.setFocusPainted(false);
        openFolderButton.setPreferredSize(new Dimension(180, 36));
        openFolderButton.addActionListener(e -> openSelectedFolder());

        JButton deleteButton = AppTheme.dangerButton("Delete Selected");
        deleteButton.setPreferredSize(new Dimension(140, 36));
        deleteButton.addActionListener(e -> deleteSelected());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        btns.add(openFolderButton);
        btns.add(downloadButton);
        btns.add(deleteButton);
        p.add(btns, BorderLayout.EAST);
        return p;
    }

    // ── Private — data ────────────────────────────────────────────────────────

    private void refreshFileList() {
        tableModel.setRowCount(0);
        String selected = (String) categoryCombo.getSelectedItem();
        List<File> files = new ArrayList<>();

        switch (selected == null ? "All Files" : selected) {
            case "All Files" -> {
                files.addAll(getFilesFromDirectory(AppDataDirectory.tradesPath()));
                files.addAll(getFilesFromDirectory(AppDataDirectory.pricesPath()));
                files.addAll(getFilesFromDirectory(AppDataDirectory.combinedFilesPath()));
                files.addAll(getFilesFromDirectory(AppDataDirectory.inventoryPath()));
            }
            case "Trades"       -> files.addAll(getFilesFromDirectory(AppDataDirectory.tradesPath()));
            case "Set Pricer"   -> files.addAll(getFilesFromDirectory(AppDataDirectory.pricesPath()));
            case "Inventory"    -> files.addAll(getFilesFromDirectory(AppDataDirectory.inventoryPath()));
            case "Combined Files" -> files.addAll(getFilesFromDirectory(AppDataDirectory.combinedFilesPath()));
        }

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (File file : files) {
            if (file.isFile()) {
                tableModel.addRow(new Object[]{
                        file.getName(), getFileType(file.getName()),
                        formatFileSize(file.length()),
                        fmt.format(new Date(file.lastModified())),
                        file.getAbsolutePath()
                });
            }
        }
        statusLabel.setText(String.format("Found %d file(s)", files.size()));
    }

    private List<File> getFilesFromDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return List.of();
        File[] arr = dir.listFiles();
        return arr != null ? Arrays.asList(arr) : List.of();
    }

    private static String getFileType(String filename) {
        if (filename.endsWith(".csv")) return "CSV";
        if (filename.endsWith(".txt")) return "Text";
        if (filename.endsWith(".pdf")) return "PDF";
        return "Other";
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ── Private — actions ─────────────────────────────────────────────────────

    private void downloadSelected() {
        int[] rows = fileTable.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(panel, "Please select files to download",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose Download Location");
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;

        File dest = chooser.getSelectedFile();
        int ok = 0, fail = 0;
        for (int row : rows) {
            try {
                int modelRow = fileTable.convertRowIndexToModel(row);
                Files.copy(new File((String) tableModel.getValueAt(modelRow, 4)).toPath(),
                        new File(dest, (String) tableModel.getValueAt(modelRow, 0)).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                ok++;
            } catch (IOException e) { fail++; }
        }
        JOptionPane.showMessageDialog(panel,
                String.format("Download complete!\n\nSuccess: %d file(s)\nFailed: %d file(s)\n\nLocation: %s",
                        ok, fail, dest.getAbsolutePath()),
                "Download Complete", JOptionPane.INFORMATION_MESSAGE);
        statusLabel.setText(String.format("Downloaded %d file(s)", ok));
    }

    private void openSelectedFolder() {
        int row = fileTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(panel, "Please select a file", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File parentDir = new File((String) tableModel.getValueAt(
                fileTable.convertRowIndexToModel(row), 4)).getParentFile();
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(parentDir);
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) Runtime.getRuntime().exec("explorer " + parentDir.getAbsolutePath());
                else if (os.contains("mac")) Runtime.getRuntime().exec("open " + parentDir.getAbsolutePath());
                else Runtime.getRuntime().exec("xdg-open " + parentDir.getAbsolutePath());
            }
            statusLabel.setText("Opened folder: " + parentDir.getName());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(panel, "Failed to open folder: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        int[] rows = fileTable.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(panel, "Please select files to delete",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(panel,
                String.format("Delete %d selected file(s)?\n\nThis cannot be undone!", rows.length),
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        int ok = 0, fail = 0;
        for (int row : rows) {
            try {
                File f = new File((String) tableModel.getValueAt(
                        fileTable.convertRowIndexToModel(row), 4));
                if (f.delete()) ok++; else fail++;
            } catch (Exception e) { fail++; }
        }
        JOptionPane.showMessageDialog(panel,
                String.format("Delete complete!\n\nDeleted: %d file(s)\nFailed: %d file(s)", ok, fail),
                "Delete Complete", JOptionPane.INFORMATION_MESSAGE);
        refreshFileList();
    }
}
