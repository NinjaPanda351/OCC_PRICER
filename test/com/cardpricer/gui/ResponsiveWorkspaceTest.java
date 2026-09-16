package com.cardpricer.gui;

import com.cardpricer.gui.panel.*;
import com.cardpricer.model.Card;
import com.cardpricer.util.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

class ResponsiveWorkspaceTest {
    @Test void homepagePriceCheckAndNameSearchRemainReachableOnSmallScreens() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            MidnightLaf.setup();AppTheme.applyFlatLafTweaks();
            MainSwingApplication app=new MainSwingApplication();JComponent root=app.createWorkspace();
            PriceCheckPanel checker=find(root,PriceCheckPanel.class);assertNotNull(checker);
            for(Dimension size:new Dimension[]{new Dimension(900,600),new Dimension(1366,768),new Dimension(1920,1080)}) {
                resize(root,size.width,size.height);
                checker.scrollRectToVisible(new Rectangle(0,0,checker.getWidth(),checker.getHeight()));
                layout(root);
                assertVisibleWithin(root,button(checker,"Check price"));
                assertVisibleWithin(root,button(checker,"Search name (F2)"));
                assertVisibleWithin(root,field(checker,"inputField",JTextField.class));
                assertVisibleWithin(root,field(checker,"priceLabel",JLabel.class));
            }
            assertNull(field(app,"tradePanel",TradePanel.class));
        });
    }
    @Test void tradeEntryAndSaveStayVisibleAndDataSurvivesResizing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MidnightLaf.setup(); AppTheme.applyFlatLafTweaks();
            MainSwingApplication app = new MainSwingApplication(); JComponent root = app.createWorkspace();
            button(root, "Start a trade").doClick();
            TradePanel trade = field(app, "tradePanel", TradePanel.class);
            try {
                Card card = new Card("Sample card", "CMM", "100"); card.setPrice("4.50"); card.setRarity("rare");
                trade.addFetchedCard(card, "", "CMM");
                JTextField customer = field(trade, "customerNameField", JTextField.class); customer.setText("Sample customer");
                JTable table = field(trade, "cardTable", JTable.class);
                for (String payment : new String[]{"Store credit", "Split payment"}) {
                    button(trade, payment).doClick();
                    for (Dimension size : new Dimension[]{new Dimension(1920,1080), new Dimension(960,640), new Dimension(900,600), new Dimension(1366,768)}) {
                        resize(root, size.width, size.height);
                        assertVisibleWithin(root, field(trade, "cardCodeField", JTextField.class));
                        assertVisibleWithin(root, button(trade, "Add card"));
                        assertVisibleWithin(root, button(trade, "Save & export"));
                        assertTrue(table.getParent().getHeight() >= table.getRowHeight() * 2,
                                "At least two rows must remain visible at " + size);
                        assertEquals(1, table.getRowCount());
                        assertEquals("Sample customer", customer.getText());
                    }
                }
                resize(root, 900, 600);
                button(trade, "Customer details").doClick(); resize(root, 900, 600);
                assertFalse(field(trade, "customerFields", JPanel.class).isVisible());
                assertEquals("Sample customer", customer.getText());
                button(trade, "Customer details").doClick(); resize(root, 900, 600);
                assertTrue(field(trade, "customerFields", JPanel.class).isVisible());
            } finally { trade.disposeResources(); }
        });
    }

    @Test void compactNavigationKeepsNamesAndCanBeExpanded() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MidnightLaf.setup(); AppTheme.applyFlatLafTweaks();
            MainSwingApplication app = new MainSwingApplication(); JComponent root = app.createWorkspace();
            resize(root, 960, 640);
            AbstractButton inventory = namedButton(root, "Inventory");
            assertEquals("", inventory.getText()); assertEquals("Inventory", inventory.getToolTipText());
            inventory.doClick(); resize(root, 960, 640);
            assertTrue(field(app, "workspaceTitle", JLabel.class).getText().endsWith("Inventory"));
            namedButton(root, "Toggle navigation").doClick(); resize(root, 960, 640);
            assertEquals("Inventory", inventory.getText());
            assertVisibleWithin(root, inventory);
        });
    }

    @Test void inventoryScrollsWideColumnsThenFillsALargerWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MidnightLaf.setup(); AppTheme.applyFlatLafTweaks();
            InventoryPanel panel = new InventoryPanel();
            JTable table = field(panel, "cardTable", JTable.class);
            JScrollPane scroll = (JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
            resize(panel, 760, 520);
            assertTrue(scroll.getHorizontalScrollBar().isVisible());
            for (int i = 0; i < table.getColumnCount(); i++)
                assertTrue(table.getColumnModel().getColumn(i).getWidth() >= table.getColumnModel().getColumn(i).getMinWidth());
            resize(panel, 1800, 900);
            assertFalse(scroll.getHorizontalScrollBar().isVisible());
            assertEquals(scroll.getViewport().getExtentSize().width, table.getWidth());
        });
    }

    @Test void settingsActionsBelowTheFoldCanBeScrolledIntoView() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MidnightLaf.setup(); AppTheme.applyFlatLafTweaks();
            PreferencesPanel panel = new PreferencesPanel();
            JTabbedPane tabs = find(panel, JTabbedPane.class); tabs.setSelectedIndex(2);
            resize(panel, 760, 480);
            AbstractButton save = button(panel, "Save Bounties");
            assertNotNull(save);
            save.scrollRectToVisible(new Rectangle(0, 0, save.getWidth(), save.getHeight()));
            layout(panel);
            assertVisibleWithin(panel, save);
        });
    }

    @Test void restoredWindowsFitSmallerScreensAndTaskbars() {
        Rectangle usable = new Rectangle(0, 0, 1280, 680);
        assertEquals(usable, WindowPlacement.fit(new Rectangle(2400,100,1920,1080), usable));
        Rectangle small = new Rectangle(0,0,800,540);
        assertEquals(small, WindowPlacement.fit(new Rectangle(-500,-500,300,200), small));
    }

    @Test void placementSupportsMonitorsWithNegativeCoordinates() {
        Rectangle usable = new Rectangle(-1920,0,1920,1040);
        Rectangle result = WindowPlacement.fitToScreens(new Rectangle(-1700,100,1200,800),
                java.util.List.of(new Rectangle(0,0,1920,1040), usable));
        assertEquals(new Rectangle(-1700,100,1200,800), result);
        assertTrue(usable.contains(result));
    }

    private static void resize(JComponent root, int width, int height) {
        root.setSize(width, height); for (int i = 0; i < 5; i++) layout(root);
    }
    private static void layout(Container container) {
        container.doLayout();
        for (Component c : container.getComponents()) if (c instanceof Container nested && c.isVisible()) layout(nested);
    }
    private static void assertVisibleWithin(JComponent root, Component component) {
        assertNotNull(component);
        Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
        assertTrue(new Rectangle(0,0,root.getWidth(),root.getHeight()).contains(bounds), "Control outside workspace: " + bounds);
        for (Container parent = component.getParent(); parent != null && parent != root; parent = parent.getParent()) {
            if (parent instanceof JViewport viewport) {
                Rectangle visible = SwingUtilities.convertRectangle(viewport, new Rectangle(0,0,viewport.getWidth(),viewport.getHeight()), root);
                assertTrue(visible.contains(bounds), "Control clipped by viewport: " + bounds + " / " + visible);
            }
        }
    }
    private static AbstractButton button(Container parent, String text) {
        for (Component c : parent.getComponents()) {
            if (c instanceof AbstractButton b && text.equals(b.getText())) return b;
            if (c instanceof Container nested) { AbstractButton b = button(nested,text); if (b != null) return b; }
        }
        return null;
    }
    private static AbstractButton namedButton(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (c instanceof AbstractButton b && name.equals(b.getAccessibleContext().getAccessibleName())) return b;
            if (c instanceof Container nested) { AbstractButton b = namedButton(nested,name); if (b != null) return b; }
        }
        return null;
    }
    private static <T> T find(Container parent, Class<T> type) {
        for (Component c : parent.getComponents()) {
            if (type.isInstance(c)) return type.cast(c);
            if (c instanceof Container nested) { T value = find(nested,type); if (value != null) return value; }
        }
        return null;
    }
    private static <T> T field(Object object, String name, Class<T> type) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(object)); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
