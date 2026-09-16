package com.cardpricer.util;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;

/** Palette defaults belong to the look and feel, making theme changes reversible. */
public final class MidnightLaf extends FlatDarkLaf {
    public static final String NAME = "OCC Midnight";
    @Override public String getName() { return NAME; }
    @Override public String getDescription() { return "Charcoal surfaces and quiet blue accents"; }
    public static boolean setup() { return setup(new MidnightLaf()); }
    @Override public UIDefaults getDefaults() {
        UIDefaults defaults = super.getDefaults();
        color(defaults, "111720", "Panel.background", "RootPane.background", "TabbedPane.background", "ScrollPane.background", "Viewport.background", "SplitPane.background");
        color(defaults, "17202C", "TextField.inactiveBackground", "TextField.disabledBackground", "Button.disabledBackground", "ToggleButton.disabledBackground");
        color(defaults, "E4EAF4", "Label.foreground", "Button.foreground", "ToggleButton.foreground", "TextField.foreground",
                "TextArea.foreground", "ComboBox.foreground", "Table.foreground", "List.foreground", "RadioButton.foreground", "CheckBox.foreground");
        color(defaults, "97A5B9", "Label.disabledForeground", "TextField.placeholderForeground", "Button.disabledText", "TitledBorder.titleColor");
        color(defaults, "1B2432", "Button.background", "ToggleButton.background", "ComboBox.background", "TextField.background", "Spinner.background");
        color(defaults, "1B2432", "ComboBox.buttonBackground", "ComboBox.buttonEditableBackground");
        color(defaults, "243147", "Button.hoverBackground", "ToggleButton.hoverBackground");
        color(defaults, "29374D", "Button.pressedBackground", "ToggleButton.pressedBackground");
        color(defaults, "29364A", "Component.borderColor", "Button.borderColor", "Button.disabledBorderColor", "Separator.foreground", "ScrollPane.borderColor");
        color(defaults, "83A9F4", "Component.focusColor", "Component.focusedBorderColor", "TabbedPane.underlineColor", "ProgressBar.foreground", "Component.accentColor");
        color(defaults, "4266AE", "Button.default.background", "Button.default.borderColor");
        color(defaults, "628BDD", "Button.default.hoverBackground", "Button.default.hoverBorderColor");
        color(defaults, "FFFFFF", "Button.default.foreground");
        color(defaults, "273A5C", "Table.selectionBackground", "List.selectionBackground", "ToggleButton.selectedBackground", "TextField.selectionBackground");
        color(defaults, "E9F0FF", "Table.selectionForeground", "List.selectionForeground", "ToggleButton.selectedForeground");
        color(defaults, "161E2A", "Table.background", "TableHeader.background", "List.background", "TextArea.background", "TextArea.inactiveBackground", "EditorPane.background", "EditorPane.inactiveBackground");
        color(defaults, "1B2432", "Spinner.buttonBackground");
        color(defaults, "111720", "ScrollBar.track");
        color(defaults, "192231", "Table.alternateRowColor");
        color(defaults, "273142", "Table.gridColor", "TableHeader.separatorColor", "TableHeader.bottomSeparatorColor");
        color(defaults, "AAB8CD", "TableHeader.foreground");
        color(defaults, "35445C", "ScrollBar.thumb", "ProgressBar.background");
        color(defaults, "0D121B", "OCC.sidebar");
        color(defaults, "192230", "OCC.surface");
        color(defaults, "1D2A3D", "OCC.raisedSurface");
        color(defaults, "243B61", "OCC.accentSurface");
        color(defaults, "83A9F4", "OCC.accent");
        color(defaults, "76D1B0", "OCC.success");
        color(defaults, "ED99A5", "OCC.danger");
        return defaults;
    }
    private static void color(UIDefaults defaults, String hex, String... keys) {
        ColorUIResource color = new ColorUIResource(Integer.parseInt(hex, 16));
        for (String key : keys) defaults.put(key, color);
    }
}
