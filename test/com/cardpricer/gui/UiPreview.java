package com.cardpricer.gui;

import com.cardpricer.gui.panel.TradePanel;
import com.cardpricer.gui.panel.PreferencesPanel;
import com.cardpricer.model.Card;
import com.cardpricer.util.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.lang.reflect.*;

/** Optional headless render of real application components using isolated sample data. */
public final class UiPreview {
    private static MainSwingApplication app;
    private static JComponent root;
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            MidnightLaf.setup(); AppTheme.applyFlatLafTweaks();
            app = new MainSwingApplication(); root = app.createWorkspace();
        });
        capture(output.resolve("overview.png"), 1440, 940);
        capture(output.resolve("overview-compact.png"), 1100, 800);
        capture(output.resolve("overview-small.png"), 960, 640);
        capture(output.resolve("overview-wide.png"), 1920, 1080);
        SwingUtilities.invokeAndWait(() -> button(root, "Start a trade").doClick());
        capture(output.resolve("trade-empty.png"), 1440, 940);
        SwingUtilities.invokeAndWait(() -> {
            TradePanel trade = trade();
            String[] names = {"Sol Ring", "Swords to Plowshares", "Rhystic Study", "Arcane Signet", "Lightning Bolt", "Counterspell"};
            String[] prices = {"4.50", "6.25", "32.00", "3.50", "7.00", "5.00"};
            for (int i = 0; i < names.length; i++) {
                Card card = new Card(names[i], "CMM", Integer.toString(100 + i));
                card.setRarity("rare"); card.setProviderId("preview-" + i); card.setPrice(prices[i]); card.setFoilPrice(prices[i]);
                trade.addFetchedCard(card, i == 2 ? "F" : "", "CMM");
            }
        });
        capture(output.resolve("trade.png"), 1440, 940);
        capture(output.resolve("trade-compact.png"), 1100, 800);
        capture(output.resolve("trade-laptop.png"), 1280, 680);
        capture(output.resolve("trade-wide.png"), 1920, 1080);
        SwingUtilities.invokeAndWait(() -> { root.setSize(1440, 940); layout(root); });
        SwingUtilities.invokeAndWait(() -> button(root, "Split payment").doClick());
        capture(output.resolve("trade-split.png"), 1440, 940);
        capture(output.resolve("trade-split-compact.png"), 1100, 800);
        capture(output.resolve("trade-small.png"), 960, 600);
        SwingUtilities.invokeAndWait(() -> navigate("inventory"));
        capture(output.resolve("inventory.png"), 1440, 940);
        capture(output.resolve("inventory-small.png"), 960, 640);
        SwingUtilities.invokeAndWait(() -> navigate("preferences"));
        capture(output.resolve("preferences.png"), 1440, 940);
        capture(output.resolve("preferences-small.png"), 960, 640);
        SwingUtilities.invokeAndWait(() -> tab(root, "Buy Rates"));
        capture(output.resolve("rates-small.png"), 960, 640);
        SwingUtilities.invokeAndWait(() -> tab(root, "Network"));
        capture(output.resolve("network-small.png"), 960, 640);
        SwingUtilities.invokeAndWait(() -> tab(root, "Card Catalog"));
        capture(output.resolve("catalog-small.png"), 960, 640);
        SwingUtilities.invokeAndWait(() -> navigate("bulk"));
        capture(output.resolve("set-pricer.png"), 1440, 940);
        capture(output.resolve("set-pricer-small.png"), 960, 640);
        SwingUtilities.invokeAndWait(() -> navigate("files"));
        capture(output.resolve("files.png"), 1440, 940);
        capture(output.resolve("files-small.png"), 960, 640);
        SwingUtilities.invokeAndWait(() -> tab(root, "History"));
        capture(output.resolve("history-small.png"), 960, 640);
        SwingUtilities.invokeAndWait(() -> {
            PreferencesPanel.applyThemeByName("FlatLaf Light"); SwingUtilities.updateComponentTreeUI(root); navigate("home");
        });
        capture(output.resolve("light-compatibility.png"), 1440, 940);
        SwingUtilities.invokeAndWait(() -> trade().disposeResources());
    }
    private static TradePanel trade() {
        try { Field field = MainSwingApplication.class.getDeclaredField("tradePanel"); field.setAccessible(true); return (TradePanel)field.get(app); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void navigate(String screen) {
        try { Method method = MainSwingApplication.class.getDeclaredMethod("showScreen", String.class, String.class); method.setAccessible(true); method.invoke(app, screen, "Preview"); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void capture(Path path, int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            root.setSize(width, height);
            for (int pass = 0; pass < 5; pass++) layout(root);
            resetTableScroll(root); layout(root);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            root.printAll(graphics); graphics.dispose();
            try { ImageIO.write(image, "png", path.toFile()); } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
    private static boolean tab(Container container, String title) {
        if (container instanceof JTabbedPane tabs) {
            for (int i = 0; i < tabs.getTabCount(); i++) if (title.equals(tabs.getTitleAt(i))) { tabs.setSelectedIndex(i); return true; }
        }
        for (Component child : container.getComponents()) if (child instanceof Container nested && tab(nested, title)) return true;
        return false;
    }
    private static void layout(Container container) {
        container.doLayout(); for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }
    private static void resetTableScroll(Container container) {
        if (container instanceof JScrollPane scroll && scroll.getViewport().getView() instanceof JTable)
            scroll.getViewport().setViewPosition(new Point(0, 0));
        for (Component child : container.getComponents()) if (child instanceof Container nested) resetTableScroll(nested);
    }
    private static AbstractButton button(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) return button;
            if (child instanceof Container nested) { AbstractButton found = button(nested, text); if (found != null) return found; }
        }
        return null;
    }
}
