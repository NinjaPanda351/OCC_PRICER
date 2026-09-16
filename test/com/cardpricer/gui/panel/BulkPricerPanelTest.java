package com.cardpricer.gui.panel;

import com.cardpricer.service.SetCatalogService;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BulkPricerPanelTest {
    private static <T> T field(BulkPricerPanel panel, String name, Class<T> type) {
        try {
            var field = BulkPricerPanel.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(panel));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test void refreshedSetsPreserveSelectionAndSearchAndCanBeExported() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new BulkPricerPanel();
            var search = field(panel, "searchField", JTextField.class);
            @SuppressWarnings("unchecked") Map<String, JCheckBox> boxes = field(panel, "setCheckboxes", Map.class);
            boxes.get("COK").setSelected(true);
            search.setText("future");
            panel.applySetCatalog(new SetCatalogService.Catalog(List.of(
                    new SetCatalogService.Entry("NEW", "new", "Future Set", "2027-01-01"),
                    new SetCatalogService.Entry("COK", "chk", "Champions of Kamigawa", "2004-10-01")), Instant.now(), ""));
            assertEquals("future", search.getText());
            assertTrue(boxes.get("COK").isSelected());
            JPanel list = field(panel, "setCheckboxPanel", JPanel.class);
            assertEquals(1, list.getComponentCount());
            assertSame(boxes.get("NEW"), list.getComponent(0));
            boxes.get("NEW").doClick();
            try {
                var method = BulkPricerPanel.class.getDeclaredMethod("getSelectedSets");
                method.setAccessible(true);
                assertEquals(List.of("NEW", "COK"), method.invoke(panel));
            } catch (Exception e) { throw new RuntimeException(e); }
            search.setText("chk");
            assertEquals(1, list.getComponentCount());
            assertSame(boxes.get("COK"), list.getComponent(0));
        });
    }
}
