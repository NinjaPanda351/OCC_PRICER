package com.cardpricer.gui.panel;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.themes.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Panel for application preferences and settings.
 */
public class PreferencesPanel extends JPanel {

    /**
     * Functional interface for a theme-application action that may throw a checked exception.
     */
    @FunctionalInterface
    private interface ThemeApplier {
        void apply() throws Exception;
    }

    private static final Preferences prefs = Preferences.userNodeForPackage(PreferencesPanel.class);
    private static final String THEME_KEY = "app.theme";

    /**
     * Registry mapping theme display names to their application actions.
     * Evaluated lazily at class-load time; new themes can be added here without
     * modifying {@link #applyThemeByName(String)}.
     */
    private static final Map<String, ThemeApplier> THEME_REGISTRY = new LinkedHashMap<>();

    static {
        // Core FlatLaf themes
        THEME_REGISTRY.put("FlatLaf Dark",        FlatDarkLaf::setup);
        THEME_REGISTRY.put("FlatLaf Light",       FlatLightLaf::setup);
        THEME_REGISTRY.put("FlatLaf Darcula",     FlatDarculaLaf::setup);
        THEME_REGISTRY.put("FlatLaf IntelliJ",    FlatIntelliJLaf::setup);
        THEME_REGISTRY.put("FlatLaf macOS Dark",  FlatMacDarkLaf::setup);
        THEME_REGISTRY.put("FlatLaf macOS Light", FlatMacLightLaf::setup);

        // IntelliJ Themes Pack (requires flatlaf-intellij-themes.jar)
        THEME_REGISTRY.put("Arc",                     () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcIJTheme"));
        THEME_REGISTRY.put("Arc Orange",              () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme"));
        THEME_REGISTRY.put("Arc Dark",                () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme"));
        THEME_REGISTRY.put("Arc Dark Orange",         () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme"));
        THEME_REGISTRY.put("Carbon",                  () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme"));
        THEME_REGISTRY.put("Cobalt 2",                () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatCobalt2IJTheme"));
        THEME_REGISTRY.put("Cyan Light",              () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme"));
        THEME_REGISTRY.put("Dark Flat",               () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme"));
        THEME_REGISTRY.put("Dark Purple",             () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme"));
        THEME_REGISTRY.put("Dracula",                 () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme"));
        THEME_REGISTRY.put("Gradianto Dark Fuchsia",  () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoDarkFuchsiaIJTheme"));
        THEME_REGISTRY.put("Gradianto Deep Ocean",    () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme"));
        THEME_REGISTRY.put("Gradianto Midnight Blue", () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme"));
        THEME_REGISTRY.put("Gradianto Nature Green",  () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoNatureGreenIJTheme"));
        THEME_REGISTRY.put("Gruvbox Dark Hard",       () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme"));
        THEME_REGISTRY.put("Gruvbox Dark Medium",     () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkMediumIJTheme"));
        THEME_REGISTRY.put("Gruvbox Dark Soft",       () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkSoftIJTheme"));
        THEME_REGISTRY.put("Hiberbee Dark",           () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme"));
        THEME_REGISTRY.put("High Contrast",           () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme"));
        THEME_REGISTRY.put("Light Flat",              () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme"));
        THEME_REGISTRY.put("Material Design Dark",    () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatMaterialDesignDarkIJTheme"));
        THEME_REGISTRY.put("Monocai",                 () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatMonocaiIJTheme"));
        THEME_REGISTRY.put("Monokai Pro",             () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme"));
        THEME_REGISTRY.put("Nord",                    () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatNordIJTheme"));
        THEME_REGISTRY.put("One Dark",                () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme"));
        THEME_REGISTRY.put("Solarized Dark",          () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme"));
        THEME_REGISTRY.put("Solarized Light",         () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme"));
        THEME_REGISTRY.put("Spacegray",               () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme"));
        THEME_REGISTRY.put("Vuesion",                 () -> UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme"));
    }

    // Available themes (derived from registry keys to keep a single source of truth)
    private static final String[] THEMES = THEME_REGISTRY.keySet().toArray(new String[0]);

    private JComboBox<String> themeCombo;
    private JButton applyButton;

    public PreferencesPanel() {
        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createSettingsPanel(), BorderLayout.CENTER);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Preferences");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Customize your application settings");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));

        panel.add(title);
        panel.add(Box.createVerticalStrut(4));
        panel.add(subtitle);

        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Theme Settings Section
        JPanel themeSection = createThemeSection();
        themeSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(themeSection);
        panel.add(Box.createVerticalStrut(20));

        // Future sections can be added here

        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private JPanel createThemeSection() {
        JPanel section = new JPanel(new BorderLayout(10, 10));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Appearance"),
                new EmptyBorder(15, 15, 15, 15)
        ));
        section.setMaximumSize(new Dimension(600, 150));

        JPanel content = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Theme selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        JLabel themeLabel = new JLabel("Theme:");
        content.add(themeLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        themeCombo = new JComboBox<>(THEMES);
        themeCombo.setPreferredSize(new Dimension(250, 32));

        // Load saved theme
        String savedTheme = prefs.get(THEME_KEY, "FlatLaf Dark");
        themeCombo.setSelectedItem(savedTheme);

        content.add(themeCombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        applyButton = new JButton("Apply");
        applyButton.setFocusPainted(false);
        applyButton.setPreferredSize(new Dimension(100, 32));
        applyButton.addActionListener(e -> applyTheme());
        content.add(applyButton, gbc);

        // Info label
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        JLabel infoLabel = new JLabel("Theme will be applied instantly to all windows");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC, 11f));
        content.add(infoLabel, gbc);

        section.add(content, BorderLayout.CENTER);

        return section;
    }

    private void applyTheme() {
        String selectedTheme = (String) themeCombo.getSelectedItem();

        try {
            // Save preference
            prefs.put(THEME_KEY, selectedTheme);

            // Apply theme immediately
            applyThemeByName(selectedTheme);

            // Update all open windows
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }

            JOptionPane.showMessageDialog(
                    this,
                    "Theme applied successfully!",
                    "Theme Changed",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to apply theme: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Gets the saved theme preference
     */
    public static String getSavedTheme() {
        return prefs.get(THEME_KEY, "FlatLaf Dark");
    }

    /**
     * Applies the saved theme at application startup
     */
    public static void applySavedTheme() {
        String theme = getSavedTheme();
        applyThemeByName(theme);
    }

    /**
     * Applies a theme by name using {@link #THEME_REGISTRY}.
     * Falls back to FlatLaf Dark if the requested theme is not found or fails to load.
     *
     * @param themeName display name of the theme to apply
     */
    private static void applyThemeByName(String themeName) {
        ThemeApplier applier = THEME_REGISTRY.getOrDefault(themeName, FlatDarkLaf::setup);
        try {
            applier.apply();
        } catch (Exception e) {
            // Fallback to dark theme if the selected theme is not available
            System.err.println("Failed to apply theme '" + themeName + "': " + e.getMessage());
            System.err.println("Note: IntelliJ themes require flatlaf-intellij-themes.jar in classpath");
            FlatDarkLaf.setup();
        }
    }
}