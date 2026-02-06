package com.cardpricer.gui.panel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Panel for managing and downloading generated data files
 */
public class FileManagerPanel extends JPanel {

    private JTable fileTable;
    private DefaultTableModel tableModel;
    private JComboBox<String> categoryCombo;
    private JLabel statusLabel;

    private static final String[] CATEGORIES = {
            "All Files",
            "Trades",
            "Set Pricer",
            "Inventory",
            "Combined Files"
    };

    public FileManagerPanel() {
        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        // Load files on startup
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

        panel.add(titlePanel, BorderLayout.NORTH);

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
            files.addAll(getFilesFromDirectory("data/trades"));
            files.addAll(getFilesFromDirectory("data/prices"));
            files.addAll(getFilesFromDirectory("data/combined_files"));
            files.addAll(getFilesFromDirectory("data/inventory"));
        } else if ("Trades".equals(selectedCategory)) {
            files.addAll(getFilesFromDirectory("data/trades"));
        } else if ("Set Pricer".equals(selectedCategory)) {
            files.addAll(getFilesFromDirectory("data/prices"));
        } else if ("Inventory".equals(selectedCategory)) {
            files.addAll(getFilesFromDirectory("data/inventory"));
        } else if ("Combined Files".equals(selectedCategory)) {
            files.addAll(getFilesFromDirectory("data/combined_files"));
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