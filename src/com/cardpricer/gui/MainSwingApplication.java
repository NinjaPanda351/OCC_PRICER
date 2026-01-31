package com.cardpricer.gui;

import com.cardpricer.gui.panel.BulkPricerPanel;
import com.cardpricer.gui.panel.TradePanel;
import com.cardpricer.gui.panel.InventoryPanel;
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

    // Screen keys
    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_BULK = "bulk";
    private static final String SCREEN_MANUAL = "manual";
    private static final String SCREEN_SEARCH = "search";
    private static final String SCREEN_TRADES = "trades";
    private static final String SCREEN_INVENTORY = "inventory";

    public static void main(String[] args) {
        // 1) Set modern look & feel
        FlatDarkLaf.setup();

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

        contentArea.add(createHomeScreen(), SCREEN_HOME);
        contentArea.add(createBulkScreen(), SCREEN_BULK);
        contentArea.add(createManualScreen(), SCREEN_MANUAL);
        contentArea.add(createComingSoonScreen("Card Search"), SCREEN_SEARCH);
        contentArea.add(createTradeScreen(), SCREEN_TRADES);
        contentArea.add(createInventoryScreen(), SCREEN_INVENTORY);

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
        JToggleButton btnManual = createNavToggle("Manual Entry", navGroup, () -> showScreen(SCREEN_MANUAL, "Manual Entry - Ready to add cards"));
        JToggleButton btnSearch = createNavToggle("Card Search", navGroup, () -> showScreen(SCREEN_SEARCH, "Card Search - Coming soon"));
        JToggleButton btnTrades = createNavToggle("Trades", navGroup, () -> showScreen(SCREEN_TRADES, "Trade Management"));
        JToggleButton btnInventory = createNavToggle("Inventory Update", navGroup, () -> showScreen(SCREEN_INVENTORY, "Inventory Update - Set card quantities"));

        sidebar.add(btnHome);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(btnBulk);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(btnManual);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(btnSearch);
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

        JButton btnPrefs = createActionButton("Preferences", () -> JOptionPane.showMessageDialog(frame, "Settings coming soon."));
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

    private JPanel createManualScreen() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Manual Card Entry");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

        JLabel subtitle = new JLabel("Enter cards one by one using set codes");
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));

        JLabel msg = new JLabel("🚧 Manual entry system coming soon!");
        msg.setBorder(new EmptyBorder(16, 0, 0, 0));

        panel.add(title);
        panel.add(Box.createVerticalStrut(6));
        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(8));
        panel.add(msg);
        return panel;
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
        cardLayout.show(contentArea, screenKey);
        statusLabel.setText(status);
    }
}