package com.cardpricer.gui.panel.filemanager;

import com.cardpricer.gui.panel.PreferencesPanel;
import com.cardpricer.util.AppDataDirectory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Shared Files tab: browse and copy files from the configured shared network folder.
 */
public final class SharedFilesTab {

    private final Runnable      onLocalRefresh;

    private JTable            sharedTable;
    private DefaultTableModel sharedTableModel;
    private JLabel            sharedStatusLabel;
    private JPanel            panel; // for dialog parenting

    /**
     * @param onLocalRefresh callback invoked after a successful copy-to-local to trigger
     *                       the local files tab to refresh its list
     */
    public SharedFilesTab(Runnable onLocalRefresh) {
        this.onLocalRefresh = onLocalRefresh;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Builds and returns the Shared Files tab panel. */
    public JPanel build() {
        panel = new JPanel(new BorderLayout(10, 10));

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
        JButton openBtn = new JButton("Open");
        openBtn.setFocusPainted(false);
        openBtn.setPreferredSize(new Dimension(110, 36));
        openBtn.addActionListener(e -> openSharedFile());

        JButton copyBtn = new JButton("Copy to Local");
        copyBtn.setFocusPainted(false);
        copyBtn.setPreferredSize(new Dimension(140, 36));
        copyBtn.addActionListener(e -> copySharedToLocal());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        btns.add(openBtn);
        btns.add(copyBtn);

        panel.add(header,  BorderLayout.NORTH);
        panel.add(scroll,  BorderLayout.CENTER);
        panel.add(btns,    BorderLayout.SOUTH);
        return panel;
    }

    /** Reloads the shared file list from the configured folder. */
    public void refresh() {
        refreshSharedFileList();
    }

    // ── Private ───────────────────────────────────────────────────────────────

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
                        f.getName(), getFileType(f.getName()),
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
            JOptionPane.showMessageDialog(panel, "Please select a file.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String filePath = (String) sharedTableModel.getValueAt(sharedTable.convertRowIndexToModel(row), 4);
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(filePath));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(panel, "Failed to open file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copySharedToLocal() {
        int row = sharedTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(panel, "Please select a file.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = sharedTable.convertRowIndexToModel(row);
        String srcPath  = (String) sharedTableModel.getValueAt(modelRow, 4);
        String filename = (String) sharedTableModel.getValueAt(modelRow, 0);
        File localDir = AppDataDirectory.trades();
        if (!localDir.exists()) localDir.mkdirs();
        File dest = new File(localDir, filename);
        try {
            Files.copy(new File(srcPath).toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(panel,
                    "Copied to local folder:\n" + dest.getAbsolutePath(),
                    "Copy Complete", JOptionPane.INFORMATION_MESSAGE);
            onLocalRefresh.run();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(panel,
                    "Failed to copy file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
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
}
