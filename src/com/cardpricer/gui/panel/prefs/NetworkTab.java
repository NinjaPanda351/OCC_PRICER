package com.cardpricer.gui.panel.prefs;

import com.cardpricer.util.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Network tab: shared trades folder configuration.
 *
 * <p>{@link #getSharedTradesFolder()} is re-exported from {@link com.cardpricer.gui.panel.PreferencesPanel}
 * as a thin wrapper so existing call sites need no changes.
 */
public final class NetworkTab {

    static final String SHARED_FOLDER_KEY = "shared.trades.folder";

    private JTextField sharedFolderField;
    private JPanel     panel; // for dialog parenting

    // ── Public API ────────────────────────────────────────────────────────────

    /** Builds and returns the Network tab panel. */
    public JPanel build() {
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 10, 10, 10));

        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Shared Trades Folder"),
                new EmptyBorder(12, 12, 12, 12)
        ));
        section.setMaximumSize(new Dimension(700, 180));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        // Row 0: label + field + Browse button
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        section.add(new JLabel("Folder path:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        sharedFolderField = new JTextField(AppearanceTab.PREFS.get(SHARED_FOLDER_KEY, ""), 28);
        sharedFolderField.setToolTipText("e.g. \\\\PC-NAME\\Trades");
        section.add(sharedFolderField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseBtn = new JButton("Browse...");
        browseBtn.setFocusPainted(false);
        browseBtn.addActionListener(e -> browseForSharedFolder());
        section.add(browseBtn, gbc);

        // Row 1: help text
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        JLabel helpLabel = new JLabel("<html>Set to a shared network folder (e.g. <code>\\\\PC-NAME\\Trades</code>) so all computers read/write the same trade files.</html>");
        helpLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11f));
        section.add(helpLabel, gbc);

        // Row 2: Test + Save buttons
        gbc.gridy = 2; gbc.gridwidth = 3;
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);

        JButton saveBtn = AppTheme.primaryButton("Save");
        saveBtn.addActionListener(e -> saveSharedFolder());

        JButton testBtn = new JButton("Test Connection");
        testBtn.setFocusPainted(false);
        testBtn.addActionListener(e -> testSharedFolder());

        btnRow.add(saveBtn);
        btnRow.add(testBtn);
        section.add(btnRow, gbc);

        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(section);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    /** Returns the configured shared trades folder path, or empty string if not set. */
    public static String getSharedTradesFolder() {
        return AppearanceTab.PREFS.get(SHARED_FOLDER_KEY, "");
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void browseForSharedFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Shared Trades Folder");
        String current = sharedFolderField.getText().trim();
        if (!current.isEmpty()) {
            File dir = new File(current);
            if (dir.exists()) chooser.setCurrentDirectory(dir);
        }
        if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
            sharedFolderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void saveSharedFolder() {
        String path = sharedFolderField.getText().trim();
        AppearanceTab.PREFS.put(SHARED_FOLDER_KEY, path);
        JOptionPane.showMessageDialog(panel,
                "Shared folder saved:\n" + (path.isEmpty() ? "(none)" : path),
                "Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private void testSharedFolder() {
        String path = sharedFolderField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "No folder path entered.", "Test", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            JOptionPane.showMessageDialog(panel, "Path does not exist or is not a folder:\n" + path,
                    "Test Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Path tmp = Files.createTempFile(dir.toPath(), ".occ_test_", ".tmp");
            Files.delete(tmp);
            JOptionPane.showMessageDialog(panel, "Connection OK — folder is accessible and writable.",
                    "Test Passed", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel, "Folder exists but is not writable:\n" + ex.getMessage(),
                    "Test Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}
