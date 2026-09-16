package com.cardpricer.gui;

import com.cardpricer.gui.dialog.HelpDialog;
import com.cardpricer.gui.panel.BulkPricerPanel;
import com.cardpricer.gui.panel.FileManagerPanel;
import com.cardpricer.gui.panel.InventoryPanel;
import com.cardpricer.gui.panel.ManagedPanel;
import com.cardpricer.gui.panel.PreferencesPanel;
import com.cardpricer.gui.panel.TradePanel;
import com.cardpricer.service.ScryfallCatalogService;
import com.cardpricer.service.TradeReceivingExportService;
import com.cardpricer.service.UpdateCheckService;
import java.util.List;
import com.cardpricer.util.AppTheme;
import com.cardpricer.util.AppVersion;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.util.prefs.Preferences;

/**
 * Application entry point and main frame for OCC Card Pricer.
 *
 * <p>Builds the sidebar navigation, the {@link CardLayout} content area, and the
 * status bar.  Panels are lazy-loaded on first access and intelligently unloaded
 * when the user navigates away, using the {@link ManagedPanel} interface to avoid
 * discarding panels with unsaved state or in-progress work.
 */
public class MainSwingApplication {

    // Main frame parts
    private JFrame frame;
    private JPanel contentArea;          // will hold screens
    private CardLayout cardLayout;       // screen switching
    private JLabel statusLabel;
    private final java.util.Map<String, JToggleButton> navigation = new java.util.LinkedHashMap<>();
    private JLabel workspaceTitle;
    private JLabel catalogChip;          // persistent catalog state indicator
    private JPanel sidebar;
    private JPanel sidebarWordmark;
    private JLabel sidebarGroup;
    private JLabel sidebarFooter;
    private JButton sidebarHelp;
    private JButton navigationToggle;
    private Boolean expandedOverride;
    private boolean sidebarExpanded = true;
    private Rectangle normalWindowBounds;

    // Update-banner slot (NORTH of root; hidden until update found)
    private JPanel updateBannerSlot;

    // Lazy-loaded panels (only created when first accessed)
    private JPanel bulkPricerPanel;
    private JPanel tradePanel;
    private JPanel fileManagerPanel;
    private JPanel inventoryPanel;
    private JPanel preferencesPanel;

    // Screen keys
    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_BULK = "bulk";
    private static final String SCREEN_FILES = "files";
    private static final String SCREEN_TRADES = "trades";
    private static final String SCREEN_INVENTORY = "inventory";
    private static final String SCREEN_PREFERENCES = "preferences";

    /**
     * Application entry point.  Applies the saved theme then launches the Swing UI
     * on the Event Dispatch Thread.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        // 1) Apply saved theme (or default to OCC Midnight)
        PreferencesPanel.applySavedTheme();

        // 2) Apply shared spacing and typography defaults
        AppTheme.applyFlatLafTweaks();

        // 3) Always start Swing on the EDT
        SwingUtilities.invokeLater(() -> new MainSwingApplication().start());
    }

    private void start() {
        frame = new JFrame("OCC Card Pricer & Trading Platform");
        try (InputStream is = MainSwingApplication.class.getResourceAsStream("/assets/OCC_Icon_400x400.png")) {
            if (is != null) frame.setIconImage(ImageIO.read(is));
        } catch (Exception ignored) {}
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (tradePanel instanceof TradePanel tp && tp.hasUnsavedCards()) {
                    int choice = JOptionPane.showConfirmDialog(frame,
                            "You have unsaved cards in the trade panel.\nExit anyway?",
                            "Unsaved Trade",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (choice != JOptionPane.YES_OPTION) return;
                }
                if (inventoryPanel instanceof ManagedPanel inventory && !inventory.isSafeToUnload()) {
                    if (JOptionPane.showConfirmDialog(frame,"Inventory is loading or has unsaved quantities. Exit?",
                            "Unsaved inventory",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
                }
                try { if (tradePanel instanceof TradePanel tp) tp.flushDraftOnClose(); }
                catch (RuntimeException failure) { JOptionPane.showMessageDialog(frame,"Draft could not be saved: "+failure.getMessage()); return; }
                saveWindowPlacement();
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setContentPane(createWorkspace());
        Rectangle screen = frame.getGraphicsConfiguration().getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(frame.getGraphicsConfiguration());
        Rectangle usable = new Rectangle(screen.x + screenInsets.left, screen.y + screenInsets.top,
                screen.width - screenInsets.left - screenInsets.right, screen.height - screenInsets.top - screenInsets.bottom);
        Preferences windowPrefs = Preferences.userNodeForPackage(MainSwingApplication.class);
        int width = windowPrefs.getInt("window.width", Math.min(1440, (int)(usable.width * .92)));
        int height = windowPrefs.getInt("window.height", Math.min(960, (int)(usable.height * .92)));
        Rectangle requested = new Rectangle(windowPrefs.getInt("window.x", usable.x + (usable.width - width) / 2),
                windowPrefs.getInt("window.y", usable.y + (usable.height - height) / 2), width, height);
        frame.setMinimumSize(new Dimension(Math.min(900, usable.width), Math.min(600, usable.height)));
        java.util.List<Rectangle> screens = new java.util.ArrayList<>();
        screens.add(usable);
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration configuration = device.getDefaultConfiguration();
            Rectangle bounds = configuration.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
            screens.add(new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
                    bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom));
        }
        normalWindowBounds = WindowPlacement.fitToScreens(requested, screens);
        frame.setBounds(normalWindowBounds);
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            private void remember() { if (frame.isShowing() && frame.getExtendedState() == JFrame.NORMAL) normalWindowBounds = frame.getBounds(); }
            @Override public void componentResized(java.awt.event.ComponentEvent e) { remember(); }
            @Override public void componentMoved(java.awt.event.ComponentEvent e) { remember(); }
        });
        if (windowPrefs.getBoolean("window.maximized", false)) frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);

        // Kick off background update check (daemon thread — never blocks UI)
        UpdateCheckService.checkAsync(banner -> {
            updateBannerSlot.add(banner, BorderLayout.CENTER);
            updateBannerSlot.revalidate();
            updateBannerSlot.repaint();
        });

        // Sync any trade files that failed to copy last session (background, best-effort)
        TradeReceivingExportService.syncMissingToSharedFolder();
        com.cardpricer.service.InventoryStatusSyncService.start();

        // Auto-load catalog: download if missing/stale (>3 days), otherwise load from disk
        startCatalogAutoLoad();
    }

    private void saveWindowPlacement() {
        Preferences prefs = Preferences.userNodeForPackage(MainSwingApplication.class);
        Rectangle bounds = normalWindowBounds;
        prefs.putInt("window.x", bounds.x); prefs.putInt("window.y", bounds.y);
        prefs.putInt("window.width", bounds.width); prefs.putInt("window.height", bounds.height);
        prefs.putBoolean("window.maximized", (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0);
    }

    /** Builds the real workspace independently of a native window for visual verification. */
    JComponent createWorkspace() {
        // Root layout
        JPanel root = new JPanel(new BorderLayout()) {
            @Override public void doLayout() {
                updateNavigation(expandedOverride != null ? expandedOverride : getWidth() >= com.formdev.flatlaf.util.UIScale.scale(1180));
                super.doLayout();
            }
        };
        root.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Update-banner slot (initially empty, populated when update found)
        updateBannerSlot = new JPanel(new BorderLayout());
        root.add(updateBannerSlot, BorderLayout.NORTH);

        // Sidebar (left)
        root.add(createSidebar(), BorderLayout.WEST);

        // Content area (center) with CardLayout
        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBorder(null);

        // Only create home screen initially - others are lazy-loaded
        contentArea.add(createHomeScreen(), SCREEN_HOME);

        // Add placeholder panels for other screens (will be replaced on first access)
        contentArea.add(new JPanel(), SCREEN_BULK);
        contentArea.add(new JPanel(), SCREEN_FILES);
        contentArea.add(new JPanel(), SCREEN_TRADES);
        contentArea.add(new JPanel(), SCREEN_INVENTORY);
        contentArea.add(new JPanel(), SCREEN_PREFERENCES);

        JPanel workspace = new JPanel(new BorderLayout());
        workspace.add(createWorkspaceHeader(), BorderLayout.NORTH);
        workspace.add(contentArea, BorderLayout.CENTER);
        root.add(workspace, BorderLayout.CENTER);

        // Status bar (bottom)
        root.add(createStatusBar(), BorderLayout.SOUTH);



        // Default screen
        showScreen(SCREEN_HOME, "Ready");

        return root;
    }

    private JComponent createWorkspaceHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createCompoundBorder(new MatteBorder(0, 0, 1, 0, AppTheme.border()), new EmptyBorder(6, 16, 6, 20)));
        navigationToggle = AppTheme.secondaryButton("");
        navigationToggle.setIcon(new AppIcon(AppIcon.Kind.MENU, 18));
        navigationToggle.setMargin(new Insets(6, 7, 6, 7));
        navigationToggle.setToolTipText("Collapse navigation");
        navigationToggle.getAccessibleContext().setAccessibleName("Toggle navigation");
        navigationToggle.addActionListener(e -> {
            expandedOverride = !sidebarExpanded;
            updateNavigation(expandedOverride);
            sidebar.getParent().revalidate();
        });
        header.add(navigationToggle, BorderLayout.WEST);
        workspaceTitle = new JLabel("Workspace  /  Overview");
        workspaceTitle.setFont(AppTheme.FONT_SMALL);
        header.add(workspaceTitle, BorderLayout.CENTER);
        header.add(AppTheme.mutedLabel("OCC  /  CARD PRICER"), BorderLayout.EAST);
        return header;
    }

    private JComponent createSidebar() {
        sidebar = new JPanel() {
            @Override public void updateUI() { super.updateUI(); setBackground(AppTheme.color("OCC.sidebar", UIManager.getColor("Panel.background"))); }
        };
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(22, 14, 18, 14));
        sidebar.setPreferredSize(new Dimension(com.formdev.flatlaf.util.UIScale.scale(208), 0));
        JPanel brand = AppTheme.transparent(new BorderLayout(12, 0));
        JLabel mark = new JLabel(new AppIcon(AppIcon.Kind.CARDS, 31)); mark.setForeground(AppTheme.accent());
        brand.add(mark, BorderLayout.WEST);
        JPanel wordmark = AppTheme.transparent(new GridLayout(2, 1, 0, 3));
        sidebarWordmark = wordmark;
        JLabel title = new JLabel("OCC"); title.setFont(AppTheme.FONT_HEADING.deriveFont(22f));
        wordmark.add(title); wordmark.add(AppTheme.mutedLabel("CARD PRICER"));
        brand.add(wordmark); brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        sidebar.add(brand); sidebar.add(Box.createVerticalStrut(22));
        JLabel groupLabel = AppTheme.eyebrow("WORKSPACE"); groupLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarGroup = groupLabel;
        sidebar.add(groupLabel); sidebar.add(Box.createVerticalStrut(12));
        ButtonGroup group = new ButtonGroup();
        sidebar.add(nav("Overview", SCREEN_HOME, AppIcon.Kind.HOME, group));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(nav("Trades", SCREEN_TRADES, AppIcon.Kind.CARDS, group));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(nav("Set pricer", SCREEN_BULK, AppIcon.Kind.STACK, group));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(nav("Inventory", SCREEN_INVENTORY, AppIcon.Kind.INVENTORY, group));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(nav("Files & history", SCREEN_FILES, AppIcon.Kind.FILES, group));
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(nav("Preferences", SCREEN_PREFERENCES, AppIcon.Kind.SETTINGS, group));
        sidebar.add(Box.createVerticalStrut(8));
        JButton help = AppTheme.secondaryButton("Help & feedback");
        sidebarHelp = help;
        help.getAccessibleContext().setAccessibleName("Help & feedback"); help.setToolTipText("Help & feedback");
        help.setIcon(new AppIcon(AppIcon.Kind.HELP)); help.setHorizontalAlignment(SwingConstants.LEFT);
        help.setIconTextGap(12); help.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        help.setAlignmentX(Component.LEFT_ALIGNMENT); help.addActionListener(e -> HelpDialog.show(frame));
        sidebar.add(help); sidebar.add(Box.createVerticalStrut(22));
        JLabel footer = AppTheme.mutedLabel("OCC Card Pricer"); footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarFooter = footer;
        sidebar.add(footer);
        for (Component child : sidebar.getComponents()) if (child instanceof JComponent c) c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sidebar;
    }

    private void updateNavigation(boolean expanded) {
        if (sidebar == null || sidebarExpanded == expanded) return;
        sidebarExpanded = expanded;
        sidebar.setPreferredSize(new Dimension(com.formdev.flatlaf.util.UIScale.scale(expanded ? 208 : 72), 0));
        sidebar.setBorder(new EmptyBorder(22, expanded ? 14 : 8, 18, expanded ? 14 : 8));
        sidebarWordmark.setVisible(expanded); sidebarGroup.setVisible(expanded); sidebarFooter.setVisible(expanded);
        for (JToggleButton button : navigation.values()) {
            button.setText(expanded ? (String)button.getClientProperty("navigation.label") : "");
            button.setHorizontalAlignment(expanded ? SwingConstants.LEFT : SwingConstants.CENTER);
            button.setMargin(new Insets(10, expanded ? 12 : 6, 10, expanded ? 12 : 6));
        }
        sidebarHelp.setText(expanded ? "Help & feedback" : "");
        sidebarHelp.setHorizontalAlignment(expanded ? SwingConstants.LEFT : SwingConstants.CENTER);
        sidebarHelp.setMargin(new Insets(9, expanded ? 12 : 6, 9, expanded ? 12 : 6));
        navigationToggle.setToolTipText(expanded ? "Collapse navigation" : "Expand navigation");
        sidebar.revalidate(); sidebar.repaint();
    }

    private JToggleButton nav(String text, String screen, AppIcon.Kind icon, ButtonGroup group) {
        JToggleButton button = new NavigationButton(text, new AppIcon(icon));
        button.putClientProperty("navigation.label", text);
        button.setToolTipText(text); button.getAccessibleContext().setAccessibleName(text);
        group.add(button); navigation.put(screen, button);
        button.setHorizontalAlignment(SwingConstants.LEFT); button.setIconTextGap(13);
        button.setFont(AppTheme.FONT_BODY); button.setMargin(new Insets(12, 14, 12, 14));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setPreferredSize(new Dimension(180, 46));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> showScreen(screen, text));
        return button;
    }

    private JComponent createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createCompoundBorder(new MatteBorder(1, 0, 0, 0, AppTheme.border()), new EmptyBorder(3, 16, 3, 16)));

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JButton version = new JButton("v" + AppVersion.CURRENT);
        version.setBorder(null); version.setContentAreaFilled(false); version.setFont(AppTheme.FONT_SMALL);
        version.setToolTipText("About OCC Card Pricer"); version.addActionListener(e -> showAboutDialog());
        version.setForeground(UIManager.getColor("Label.disabledForeground"));

        // F8: Theme toggle button (☀ = switch to light, 🌙 = switch to dark)
        JButton themeToggleBtn = AppTheme.secondaryButton("");
        themeToggleBtn.setIcon(new AppIcon(isDarkTheme() ? AppIcon.Kind.SUN : AppIcon.Kind.MOON, 16));
        themeToggleBtn.setToolTipText("Toggle dark/light theme");
        themeToggleBtn.getAccessibleContext().setAccessibleName("Toggle dark/light theme");
        themeToggleBtn.setPreferredSize(new Dimension(30, 26));
        themeToggleBtn.setMargin(new Insets(3, 5, 3, 5));
        themeToggleBtn.addActionListener(e -> {
            toggleTheme();
            themeToggleBtn.setIcon(new AppIcon(isDarkTheme() ? AppIcon.Kind.SUN : AppIcon.Kind.MOON, 16));
        });

        catalogChip = new JLabel("\u25CB Catalog");
        catalogChip.setForeground(UIManager.getColor("Label.disabledForeground"));
        catalogChip.setFont(catalogChip.getFont().deriveFont(11f));
        catalogChip.setToolTipText("Card catalog not loaded");

        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        eastPanel.setOpaque(false);
        eastPanel.add(catalogChip);
        eastPanel.add(themeToggleBtn);
        eastPanel.add(version);

        status.add(statusLabel, BorderLayout.CENTER);
        status.add(eastPanel, BorderLayout.EAST);
        return status;
    }

    /** Returns {@code true} if the currently saved theme is a dark theme. */
    private boolean isDarkTheme() { return com.formdev.flatlaf.FlatLaf.isLafDark(); }

    private void toggleTheme() {
        String next = isDarkTheme() ? "FlatLaf Light" : com.cardpricer.util.MidnightLaf.NAME;
        Preferences.userNodeForPackage(PreferencesPanel.class).put("app.theme", next);
        PreferencesPanel.applyThemeByName(next);
        for (Window window : Window.getWindows()) SwingUtilities.updateComponentTreeUI(window);
    }

    private JPanel createHomeScreen() {
        return new com.cardpricer.gui.panel.DashboardPanel(screen -> {
            String target = "history".equals(screen) ? SCREEN_FILES : screen;
            showScreen(target, (String)navigation.get(target).getClientProperty("navigation.label"));
            if ("history".equals(screen)) ((FileManagerPanel) fileManagerPanel).showHistory();
        });
    }

    private JPanel createBulkScreen() {
        return new BulkPricerPanel();
    }

    private JPanel createTradeScreen() {
        return new TradePanel();
    }

    private JPanel createInventoryScreen() {
        return new InventoryPanel();
    }

    private JPanel createFileManagerScreen() {
        FileManagerPanel panel=new FileManagerPanel();
        panel.setTradeEditor(draft -> {
            showScreen(SCREEN_TRADES,(String)navigation.get(SCREEN_TRADES).getClientProperty("navigation.label"));
            SwingUtilities.invokeLater(() -> ((TradePanel)tradePanel).editSavedTrade(draft));
        });
        return panel;
    }

    private JPanel createPreferencesScreen() {
        return new  PreferencesPanel();
    }

    private JPanel createComingSoonScreen(String featureName) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel("🚧");
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        icon.setFont(icon.getFont().deriveFont(44f));

        JLabel title = new JLabel(featureName);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

        JLabel msg = new JLabel("This feature is coming soon!");
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);
        msg.setForeground(UIManager.getColor("Label.disabledForeground"));

        panel.add(Box.createVerticalGlue());
        panel.add(icon);
        panel.add(Box.createVerticalStrut(10));
        panel.add(title);
        panel.add(Box.createVerticalStrut(8));
        panel.add(msg);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    /**
     * Starts a background worker that loads or downloads the Scryfall card catalog:
     * <ul>
     *   <li>Already loaded — skips immediately.</li>
     *   <li>Cache missing or older than 2 days — downloads and builds from Scryfall.</li>
     *   <li>Cache present and fresh — loads from disk into memory.</li>
     * </ul>
     * Progress is reported in the status bar; the UI is never blocked.
     */
    private void startCatalogAutoLoad() {
        ScryfallCatalogService catalog = ScryfallCatalogService.getInstance();
        if (catalog.isLoaded()) return;

        if (!catalog.isCatalogAvailable()) {
            // No local cache at all — nudge user to download via Preferences
            catalogChip.setText("\u25CB Catalog");
            catalogChip.setForeground(UIManager.getColor("Label.disabledForeground"));
            catalogChip.setToolTipText("No catalog — download via Preferences → Catalog");
            statusLabel.setText("Catalog not downloaded — go to Preferences to download");
            return;
        }

        boolean stale = catalog.getCacheAgeMs() > 2 * 86_400_000L;

        // Amber ⟳ while downloading or loading
        catalogChip.setText("\u29D7 Catalog");
        catalogChip.setForeground(new Color(0xD97706));

        if (stale) {
            catalogChip.setToolTipText("Downloading fresh catalog\u2026");
            statusLabel.setText("Catalog is over 2 days old \u2014 downloading update\u2026");

            new SwingWorker<String, String>() {
                @Override
                protected String doInBackground() throws Exception {
                    catalog.loadFromDisk();
                    publish("Using the saved catalog while refreshing...");
                    catalog.downloadAndBuild(new ScryfallCatalogService.DownloadProgress() {
                        @Override public boolean isCancelled() { return false; }
                        @Override public void onUpdate(int count, String phase) { publish(phase); }
                    });
                    return "Catalog updated \u2014 " + String.format("%,d", catalog.getCardCount()) + " cards";
                }

                @Override
                protected void process(List<String> chunks) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }

                @Override
                protected void done() {
                    try {
                        statusLabel.setText(get());
                        catalogChip.setText("\u25CF Catalog");
                        catalogChip.setForeground(new Color(0x22C55E));
                        catalogChip.setToolTipText(
                                String.format("%,d cards  (fresh)", catalog.getCardCount()));
                    } catch (Exception ex) {
                        statusLabel.setText(catalog.isLoaded() ? "Using saved catalog; refresh failed" : "Catalog unavailable; open Preferences to retry");
                        catalogChip.setText("\u25CB Catalog");
                        catalogChip.setForeground(UIManager.getColor("Label.disabledForeground"));
                        catalogChip.setToolTipText("Catalog failed — check Preferences");
                    }
                }
            }.execute();
        } else {
            catalogChip.setToolTipText("Loading card catalog from disk\u2026");

            new SwingWorker<String, String>() {
                @Override
                protected String doInBackground() throws Exception {
                    publish("Loading card catalog\u2026");
                    catalog.loadFromDisk();
                    return "Catalog ready \u2014 " + String.format("%,d", catalog.getCardCount()) + " cards";
                }

                @Override
                protected void process(List<String> chunks) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }

                @Override
                protected void done() {
                    try {
                        statusLabel.setText(get());
                        catalogChip.setText("\u25CF Catalog");
                        catalogChip.setForeground(new Color(0x22C55E));
                        long ageMs   = catalog.getCacheAgeMs();
                        long ageDays = ageMs / 86_400_000L;
                        String ageStr = ageDays > 0 ? ageDays + "d old" : "fresh";
                        catalogChip.setToolTipText(
                                String.format("%,d cards  (%s)", catalog.getCardCount(), ageStr));
                    } catch (Exception ex) {
                        statusLabel.setText("Catalog failed to load \u2014 open Preferences to retry");
                        catalogChip.setText("\u25CB Catalog");
                        catalogChip.setForeground(UIManager.getColor("Label.disabledForeground"));
                        catalogChip.setToolTipText("Catalog failed — check Preferences");
                    }
                }
            }.execute();
        }
    }

    private void showAboutDialog() {
        String text =
                "A comprehensive Magic: The Gathering card pricing and trading platform.\n\n" +
                        "Features:\n" +
                        "• Full Set price fetching from Scryfall\n" +
                        "• Manual card entry\n" +
                        "• Trade management\n" +
                        "• Card search\n\n" +
                        "© 2025 OCC Card Pricer";
        JOptionPane.showMessageDialog(frame, text, "About OCC Card Pricer", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showScreen(String screenKey, String status) {
        // Smart memory management: unload panels that aren't being viewed (except those with active work)
        unloadInactivePanels(screenKey);

        // Lazy load panels on first access
        boolean needsRevalidate = false;

        switch (screenKey) {
            case SCREEN_BULK:
                if (bulkPricerPanel == null) {
                    bulkPricerPanel = createBulkScreen();
                    // Remove all components and re-add with real panel
                    Component[] components = contentArea.getComponents();
                    for (int i = 0; i < components.length; i++) {
                        Component comp = components[i];
                        // Check if this is the bulk screen position (index 1)
                        if (i == 1) { // SCREEN_BULK is second component
                            contentArea.remove(comp);
                            contentArea.add(bulkPricerPanel, SCREEN_BULK, i);
                            needsRevalidate = true;
                            break;
                        }
                    }
                }
                break;

            case SCREEN_FILES:
                if (fileManagerPanel == null) {
                    fileManagerPanel = createFileManagerScreen();
                    Component[] components = contentArea.getComponents();
                    for (int i = 0; i < components.length; i++) {
                        if (i == 2) { // SCREEN_FILES is third component
                            contentArea.remove(components[i]);
                            contentArea.add(fileManagerPanel, SCREEN_FILES, i);
                            needsRevalidate = true;
                            break;
                        }
                    }
                }
                break;

            case SCREEN_TRADES:
                if (tradePanel == null) {
                    tradePanel = createTradeScreen();
                    Component[] components = contentArea.getComponents();
                    for (int i = 0; i < components.length; i++) {
                        if (i == 3) { // SCREEN_TRADES is fourth component
                            contentArea.remove(components[i]);
                            contentArea.add(tradePanel, SCREEN_TRADES, i);
                            needsRevalidate = true;
                            break;
                        }
                    }
                }
                break;

            case SCREEN_INVENTORY:
                if (inventoryPanel == null) {
                    inventoryPanel = createInventoryScreen();
                    Component[] components = contentArea.getComponents();
                    for (int i = 0; i < components.length; i++) {
                        if (i == 4) { // SCREEN_INVENTORY is fifth component
                            contentArea.remove(components[i]);
                            contentArea.add(inventoryPanel, SCREEN_INVENTORY, i);
                            needsRevalidate = true;
                            break;
                        }
                    }
                }
                break;

            case SCREEN_PREFERENCES:
                if (preferencesPanel == null) {
                    preferencesPanel = createPreferencesScreen();
                    Component[] components = contentArea.getComponents();
                    for (int i = 0; i < components.length; i++) {
                        if (i == 5) { // SCREEN_PREFERENCES is sixth component
                            contentArea.remove(components[i]);
                            contentArea.add(preferencesPanel, SCREEN_PREFERENCES, i);
                            needsRevalidate = true;
                            break;
                        }
                    }
                }
                break;
        }

        if (needsRevalidate) {
            contentArea.revalidate();
            contentArea.repaint();
        }

        cardLayout.show(contentArea, screenKey);
        statusLabel.setText(status);
        JToggleButton active = navigation.get(screenKey);
        if (active != null) active.setSelected(true);
        if (workspaceTitle != null) workspaceTitle.setText("Workspace  /  " + (active == null ? status : active.getClientProperty("navigation.label")));
    }

    /**
     * Unloads inactive panels to free memory, skipping panels that report they are not safe
     * to unload via the {@link ManagedPanel} interface.
     *
     * <ul>
     *   <li>File Manager — always unloaded when not visible (stateless).</li>
     *   <li>Bulk Pricer — kept alive while a fetch {@code SwingWorker} is running
     *       ({@link ManagedPanel#isSafeToUnload()} returns {@code false}).</li>
     *   <li>Inventory — kept alive while a set is loaded in the table
     *       ({@link ManagedPanel#isSafeToUnload()} returns {@code false}).</li>
     *   <li>Trade Panel — never unloaded; user data must never be lost mid-session.</li>
     * </ul>
     *
     * @param activeScreenKey the screen key of the panel the user just navigated to
     */
    private void unloadInactivePanels(String activeScreenKey) {
        // File Manager - always safe to unload (no state to preserve)
        if (!SCREEN_FILES.equals(activeScreenKey) && fileManagerPanel != null) {
            Component[] components = contentArea.getComponents();
            for (int i = 0; i < components.length; i++) {
                if (i == 2) { // SCREEN_FILES position
                    contentArea.remove(components[i]);
                    contentArea.add(new JPanel(), SCREEN_FILES, i);
                    fileManagerPanel = null;
                    break;
                }
            }
        }

        // Bulk Pricer - only unload if safe (no active fetch worker)
        if (!SCREEN_BULK.equals(activeScreenKey) && bulkPricerPanel != null) {
            boolean isSafe = !(bulkPricerPanel instanceof ManagedPanel)
                    || ((ManagedPanel) bulkPricerPanel).isSafeToUnload();
            if (isSafe) {
                Component[] components = contentArea.getComponents();
                for (int i = 0; i < components.length; i++) {
                    if (i == 1) { // SCREEN_BULK position
                        contentArea.remove(components[i]);
                        contentArea.add(new JPanel(), SCREEN_BULK, i);
                        bulkPricerPanel = null;
                        break;
                    }
                }
            }
        }

        // Inventory - only unload if safe (no cards currently loaded)
        if (!SCREEN_INVENTORY.equals(activeScreenKey) && inventoryPanel != null) {
            boolean isSafe = !(inventoryPanel instanceof ManagedPanel)
                    || ((ManagedPanel) inventoryPanel).isSafeToUnload();
            if (isSafe) {
                Component[] components = contentArea.getComponents();
                for (int i = 0; i < components.length; i++) {
                    if (i == 4) { // SCREEN_INVENTORY position
                        contentArea.remove(components[i]);
                        contentArea.add(new JPanel(), SCREEN_INVENTORY, i);
                        inventoryPanel = null;
                        break;
                    }
                }
            }
        }

        // Trade Panel - NEVER unload (always keep trade data)
        // This ensures user never loses trade work in progress
    }
}
