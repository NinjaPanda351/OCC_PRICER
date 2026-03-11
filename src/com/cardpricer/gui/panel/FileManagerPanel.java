package com.cardpricer.gui.panel;

import com.cardpricer.gui.ShortcutHelpDialog;
import com.cardpricer.gui.panel.filemanager.HistoryTab;
import com.cardpricer.gui.panel.filemanager.LocalFilesTab;
import com.cardpricer.gui.panel.filemanager.SharedFilesTab;
import com.cardpricer.util.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * File Manager coordinator — wires the three file-browsing tabs.
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

    /** Constructs the File Manager panel and loads the local file list immediately. */
    public FileManagerPanel() {
        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(createTopPanel(), BorderLayout.NORTH);

        LocalFilesTab  localTab  = new LocalFilesTab();
        HistoryTab     historyTab = new HistoryTab();
        SharedFilesTab sharedTab  = new SharedFilesTab(localTab::refresh);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Local Files",  localTab.build());
        tabs.addTab("Shared Files", sharedTab.build());
        tabs.addTab("History",      historyTab.build());

        tabs.addChangeListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 1) sharedTab.refresh();
            if (idx == 2) historyTab.refresh();
        });

        add(tabs, BorderLayout.CENTER);

        localTab.refresh();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private JPanel createTopPanel() {
        JPanel titlePanel = AppTheme.panelHeader("File Manager",
                "Browse generated files and trade history");

        JButton helpBtn = new JButton("?");
        helpBtn.setFocusPainted(false);
        helpBtn.setPreferredSize(new Dimension(34, 34));
        helpBtn.setFont(helpBtn.getFont().deriveFont(Font.BOLD, 14f));
        helpBtn.setToolTipText("Help");
        helpBtn.addActionListener(e ->
                ShortcutHelpDialog.show(SwingUtilities.getWindowAncestor(this),
                        HELP_TITLE, HELP_COLS, HELP_ROWS));

        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.add(titlePanel, BorderLayout.CENTER);
        panel.add(helpBtn,    BorderLayout.EAST);
        return panel;
    }
}
