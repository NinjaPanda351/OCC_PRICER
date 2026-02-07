package com.cardpricer.gui;

import com.cardpricer.gui.panel.BulkPricerPanel;
import com.cardpricer.gui.panel.FileManagerPanel;
import com.cardpricer.gui.panel.InventoryPanel;
import com.cardpricer.gui.panel.PreferencesPanel;
import com.cardpricer.gui.panel.TradePanel;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainSwingApplication {

    // Main frame parts
    private JFrame frame;
    private JPanel contentArea;          // will hold screens
    private CardLayout cardLayout;       // screen switching
    private JLabel statusLabel;

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

    public static void main(String[] args) {
        // 1) Apply saved theme (or default to FlatLaf Dark)
        PreferencesPanel.applySavedTheme();

        // 2) Always start Swing on the EDT
        SwingUtilities.invokeLater(() -> new MainSwingApplication().start());
    }

    private void start() {
        frame = new JFrame("OCC Card Pricer & Trading Platform");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1100, 650));

        // Root layout
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Sidebar (left)
        root.add(createSidebar(), BorderLayout.WEST);

        // Content area (center) with CardLayout
        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Only create home screen initially - others are lazy-loaded
        contentArea.add(createHomeScreen(), SCREEN_HOME);

        // Add placeholder panels for other screens (will be replaced on first access)
        contentArea.add(new JPanel(), SCREEN_BULK);
        contentArea.add(new JPanel(), SCREEN_FILES);
        contentArea.add(new JPanel(), SCREEN_TRADES);
        contentArea.add(new JPanel(), SCREEN_INVENTORY);
        contentArea.add(new JPanel(), SCREEN_PREFERENCES);

        root.add(contentArea, BorderLayout.CENTER);

        // Status bar (bottom)
        root.add(createStatusBar(), BorderLayout.SOUTH);

        frame.setContentPane(root);

        // Default screen
        showScreen(SCREEN_HOME, "Ready");

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JComponent createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(18, 18, 18, 18));
        sidebar.setPreferredSize(new Dimension(260, 0));

        JLabel title = new JLabel("OCC Card Pricer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JLabel subtitle = new JLabel("Trading Card Platform");
        subtitle.setFont(subtitle.getFont().deriveFont(12f));
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));

        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(subtitle);
        sidebar.add(Box.createVerticalStrut(16));
        sidebar.add(new JSeparator());
        sidebar.add(Box.createVerticalStrut(12));

        JLabel navLabel = new JLabel("NAVIGATION");
        navLabel.setFont(navLabel.getFont().deriveFont(Font.BOLD, 11f));
        navLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        sidebar.add(navLabel);
        sidebar.add(Box.createVerticalStrut(8));

        // Make nav buttons behave like ToggleButtons (radio group)
        ButtonGroup navGroup = new ButtonGroup();

        JToggleButton btnHome = createNavToggle("Home", navGroup, () -> showScreen(SCREEN_HOME, "Ready"));
        JToggleButton btnBulk = createNavToggle("Set Pricer", navGroup, () -> showScreen(SCREEN_BULK, "Set Pricer - Ready to process sets"));
        JToggleButton btnFiles = createNavToggle("File Manager", navGroup, () -> showScreen(SCREEN_FILES, "File Manager - Download generated files"));
        JToggleButton btnTrades = createNavToggle("Trades", navGroup, () -> showScreen(SCREEN_TRADES, "Trade Management - Ready"));
        JToggleButton btnInventory = createNavToggle("Inventory Update", navGroup, () -> showScreen(SCREEN_INVENTORY, "Inventory Update - Set card quantities"));

        sidebar.add(btnHome);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(btnBulk);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(btnFiles);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(btnTrades);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(btnInventory);

        sidebar.add(Box.createVerticalGlue());
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(new JSeparator());
        sidebar.add(Box.createVerticalStrut(12));

        JLabel settingsLabel = new JLabel("SETTINGS");
        settingsLabel.setFont(settingsLabel.getFont().deriveFont(Font.BOLD, 11f));
        settingsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        sidebar.add(settingsLabel);
        sidebar.add(Box.createVerticalStrut(8));

        JButton btnPrefs = createActionButton("Preferences", () -> showScreen("preferences", "Preferences"));
        JButton btnAbout = createActionButton("About", this::showAboutDialog);

        sidebar.add(btnPrefs);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(btnAbout);

        // Default selection
        btnHome.setSelected(true);

        return sidebar;
    }

    private JToggleButton createNavToggle(String text, ButtonGroup group, Runnable onClick) {
        JToggleButton b = new JToggleButton(text);
        group.add(b);

        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(10, 12, 10, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // FlatLaf supports styling via client properties
        b.putClientProperty("JButton.buttonType", "roundRect");

        b.addActionListener(e -> onClick.run());
        return b;
    }

    private JButton createActionButton(String text, Runnable onClick) {
        JButton b = new JButton(text);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(10, 12, 10, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.addActionListener(e -> onClick.run());
        return b;
    }

    private JComponent createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(new EmptyBorder(8, 12, 8, 12));

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JLabel version = new JLabel("v1.0.0");
        version.setForeground(UIManager.getColor("Label.disabledForeground"));

        status.add(statusLabel, BorderLayout.WEST);
        status.add(version, BorderLayout.EAST);
        return status;
    }

    private JPanel createHomeScreen() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Welcome to OCC Card Pricer");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));

        JLabel subtitle = new JLabel("Your all-in-one Magic: The Gathering card management platform");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        subtitle.setFont(subtitle.getFont().deriveFont(14f));

        panel.add(Box.createVerticalGlue());
        panel.add(title);
        panel.add(Box.createVerticalStrut(10));
        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(20));

        JPanel cards = new JPanel(new GridLayout(1, 3, 16, 16));
        cards.add(createFeatureCard("Set pricer", "Fetch prices for entire sets"));
        cards.add(createFeatureCard("Manual Entry", "Add individual cards"));
        cards.add(createFeatureCard("Search", "Find cards instantly"));

        panel.add(cards);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JComponent createFeatureCard(String title, String description) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));
        card.putClientProperty("JComponent.roundRect", true);

        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 16f));

        JLabel d = new JLabel("<html><div style='width:180px;'>" + description + "</div></html>");
        d.setForeground(UIManager.getColor("Label.disabledForeground"));

        card.add(t);
        card.add(Box.createVerticalStrut(8));
        card.add(d);
        return card;
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
        return new FileManagerPanel();
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

    private void showAboutDialog() {
        String text =
                "A comprehensive Magic: The Gathering card pricing and trading platform.\n\n" +
                        "Features:\n" +
                        "• Full Set price fetching from Scryfall\n" +
                        "• Manual card entry\n" +
                        "• Trade management (coming soon)\n" +
                        "• Card search (coming soon)\n\n" +
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
    }

    /**
     * Unloads inactive panels to free memory, but preserves panels with active work
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

        // Bulk Pricer - only unload if NOT currently processing
        if (!SCREEN_BULK.equals(activeScreenKey) && bulkPricerPanel != null) {
            // Check if bulk pricer is currently processing
            boolean isProcessing = isBulkPricerProcessing();
            if (!isProcessing) {
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

        // Inventory - only unload if no cards loaded
        if (!SCREEN_INVENTORY.equals(activeScreenKey) && inventoryPanel != null) {
            boolean hasLoadedCards = isInventoryPanelLoaded();
            if (!hasLoadedCards) {
                Component[] components = contentArea.getComponents();
                for (int i = 0; i < components.length; i++) {
                    if (i == 4) { // SCREEN_INVENTORY position (now index 4)
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

    /**
     * Checks if bulk pricer is currently processing sets
     */
    private boolean isBulkPricerProcessing() {
        if (bulkPricerPanel == null) return false;

        try {
            // Use reflection to check if worker is running
            java.lang.reflect.Field[] fields = bulkPricerPanel.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (field.getName().contains("worker") || field.getName().contains("Worker")) {
                    field.setAccessible(true);
                    Object worker = field.get(bulkPricerPanel);
                    if (worker instanceof SwingWorker) {
                        SwingWorker<?, ?> sw = (SwingWorker<?, ?>) worker;
                        return !sw.isDone();
                    }
                }
            }
        } catch (Exception e) {
            // If reflection fails, assume not processing
        }

        return false;
    }

    /**
     * Checks if inventory panel has loaded cards
     */
    private boolean isInventoryPanelLoaded() {
        if (inventoryPanel == null) return false;

        try {
            // Use reflection to check if cards are loaded
            java.lang.reflect.Field loadedCardsField = inventoryPanel.getClass().getDeclaredField("loadedCards");
            loadedCardsField.setAccessible(true);
            Object loadedCardsObj = loadedCardsField.get(inventoryPanel);

            // Check using reflection methods instead of casting to List<?>
            if (loadedCardsObj != null) {
                java.lang.reflect.Method sizeMethod = loadedCardsObj.getClass().getMethod("size");
                Integer size = (Integer) sizeMethod.invoke(loadedCardsObj);
                return size != null && size > 0;
            }
            return false;
        } catch (Exception e) {
            // If reflection fails, assume not loaded (safe to unload)
            return false;
        }
    }
}