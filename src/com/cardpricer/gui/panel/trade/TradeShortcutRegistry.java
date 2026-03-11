package com.cardpricer.gui.panel.trade;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Map;

/**
 * Static helper that registers keyboard shortcuts into a component's InputMap/ActionMap.
 *
 * <p>Replaces the repetitive {@code panelIM.put / panelAM.put} / anonymous {@code AbstractAction}
 * blocks in TradePanel, reducing boilerplate to a single descriptive call.
 */
public final class TradeShortcutRegistry {

    private TradeShortcutRegistry() {}

    /**
     * Registers shortcuts on a panel using {@code WHEN_IN_FOCUSED_WINDOW} scope.
     *
     * @param panel    the panel to register on
     * @param bindings map of KeyStroke → action runnable
     */
    public static void registerPanelShortcuts(JPanel panel, Map<KeyStroke, Runnable> bindings) {
        InputMap  im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = panel.getActionMap();
        register(im, am, bindings);
    }

    /**
     * Registers shortcuts on a table using {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT} scope.
     *
     * @param table    the table to register on
     * @param bindings map of KeyStroke → action runnable
     */
    public static void registerTableShortcuts(JTable table, Map<KeyStroke, Runnable> bindings) {
        InputMap  im = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = table.getActionMap();
        register(im, am, bindings);
    }

    private static void register(InputMap im, ActionMap am, Map<KeyStroke, Runnable> bindings) {
        for (Map.Entry<KeyStroke, Runnable> entry : bindings.entrySet()) {
            String key = entry.getKey().toString();
            Runnable action = entry.getValue();
            im.put(entry.getKey(), key);
            am.put(key, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    action.run();
                }
            });
        }
    }
}
