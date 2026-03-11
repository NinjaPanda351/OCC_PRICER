package com.cardpricer.gui.panel;

import com.cardpricer.gui.ShortcutHelpDialog;
import com.cardpricer.gui.panel.prefs.AppearanceTab;
import com.cardpricer.gui.panel.prefs.BuyRatesTab;
import com.cardpricer.gui.panel.prefs.CatalogTab;
import com.cardpricer.gui.panel.prefs.NetworkTab;
import com.cardpricer.util.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Preferences coordinator — wires the four settings tabs and re-exports the
 * static utility methods consumed by the rest of the application.
 */
public class PreferencesPanel extends JPanel {

    private static final String   HELP_TITLE = "Preferences — Help";
    private static final String[] HELP_COLS  = {"Setting", "Description"};
    private static final String[][] HELP_ROWS = {
        {"--- Appearance", ""},
        {"Theme",           "Select and apply a UI colour theme instantly"},
        {"--- Network", ""},
        {"Shared Folder",   "Path to a network folder shared across workstations"},
        {"Browse",          "Pick the shared folder via a folder chooser"},
        {"Test Connection", "Verify the folder exists and is writable"},
        {"Save",            "Persist the shared folder path"},
        {"--- Buy Rates — Rules", ""},
        {"Min Price ($)",   "Cards above this value use this row's rates"},
        {"Credit % / Check %", "Payout percentages for this price tier"},
        {"$0.00 row",       "Catch-all rate — cannot be removed"},
        {"Save Rules",      "Persist rule changes to disk"},
        {"--- Buy Rates — Bounties", ""},
        {"Add Bounty",      "Override buy rate for a specific card by name"},
        {"Import CSV",      "Bulk-import bounties: CARD NAME,CREDIT%,CHECK%"},
        {"Import and Add",  "Merge CSV rows — existing names overwritten"},
        {"Import and Replace", "Clear all bounties first, then import"},
        {"Export CSV",      "Export current bounty table to a CSV file"},
        {"Save Bounties",   "Persist bounty changes to disk"},
        {"--- Card Catalog", ""},
        {"Download Catalog", "Download the full Scryfall card list for instant paste-import (~30 MB download)"},
        {"Load into Memory", "Load an existing catalog file into memory for this session"},
    };

    public PreferencesPanel() {
        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createTabbedSettings(), BorderLayout.CENTER);
    }

    // ── Static API (thin wrappers — call sites unchanged) ─────────────────────

    /** Returns the configured shared trades folder path, or empty string if not set. */
    public static String getSharedTradesFolder()          { return NetworkTab.getSharedTradesFolder(); }

    /** Returns the persisted theme display name, defaulting to {@code "FlatLaf Dark"}. */
    public static String getSavedTheme()                  { return AppearanceTab.getSavedTheme(); }

    /** Reads the saved theme preference and applies it immediately. */
    public static void applySavedTheme()                  { AppearanceTab.applySavedTheme(); }

    /** Applies a theme by name; falls back to FlatLaf Dark on failure. */
    public static void applyThemeByName(String themeName) { AppearanceTab.applyThemeByName(themeName); }

    // ── Private ───────────────────────────────────────────────────────────────

    private JPanel createTopPanel() {
        JPanel titlePanel = AppTheme.panelHeader("Preferences", "Appearance, network & buy rates");

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

    private JTabbedPane createTabbedSettings() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Appearance",   new AppearanceTab().build());
        tabs.addTab("Network",      new NetworkTab().build());
        tabs.addTab("Buy Rates",    new BuyRatesTab().build());
        tabs.addTab("Card Catalog", new CatalogTab().build());
        return tabs;
    }
}
