package com.cardpricer.gui.panel;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.themes.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * Panel for application preferences and settings
 */
public class PreferencesPanel extends JPanel {

    private static final Preferences prefs = Preferences.userNodeForPackage(PreferencesPanel.class);
    private static final String THEME_KEY = "app.theme";

    // Available themes
    private static final String[] THEMES = {
            "FlatLaf Dark",
            "FlatLaf Light",
            "FlatLaf Darcula",
            "FlatLaf IntelliJ",
            "FlatLaf macOS Dark",
            "FlatLaf macOS Light",
            "Arc",
            "Arc Orange",
            "Arc Dark",
            "Arc Dark Orange",
            "Carbon",
            "Cobalt 2",
            "Cyan Light",
            "Dark Flat",
            "Dark Purple",
            "Dracula",
            "Gradianto Dark Fuchsia",
            "Gradianto Deep Ocean",
            "Gradianto Midnight Blue",
            "Gradianto Nature Green",
            "Gruvbox Dark Hard",
            "Gruvbox Dark Medium",
            "Gruvbox Dark Soft",
            "Hiberbee Dark",
            "High Contrast",
            "Light Flat",
            "Material Design Dark",
            "Monocai",
            "Monokai Pro",
            "Nord",
            "One Dark",
            "Solarized Dark",
            "Solarized Light",
            "Spacegray",
            "Vuesion"
    };

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
     * Applies a theme by name
     */
    private static void applyThemeByName(String themeName) {
        try {
            switch (themeName) {
                // Core FlatLaf themes
                case "FlatLaf Dark":
                    FlatDarkLaf.setup();
                    break;
                case "FlatLaf Light":
                    FlatLightLaf.setup();
                    break;
                case "FlatLaf Darcula":
                    FlatDarculaLaf.setup();
                    break;
                case "FlatLaf IntelliJ":
                    FlatIntelliJLaf.setup();
                    break;
                case "FlatLaf macOS Dark":
                    FlatMacDarkLaf.setup();
                    break;
                case "FlatLaf macOS Light":
                    FlatMacLightLaf.setup();
                    break;

                // IntelliJ Themes Pack (require flatlaf-intellij-themes.jar)
                case "Arc":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcIJTheme");
                    break;
                case "Arc Orange":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme");
                    break;
                case "Arc Dark":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme");
                    break;
                case "Arc Dark Orange":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme");
                    break;
                case "Carbon":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme");
                    break;
                case "Cobalt 2":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatCobalt2IJTheme");
                    break;
                case "Cyan Light":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme");
                    break;
                case "Dark Flat":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme");
                    break;
                case "Dark Purple":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme");
                    break;
                case "Dracula":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme");
                    break;
                case "Gradianto Dark Fuchsia":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoDarkFuchsiaIJTheme");
                    break;
                case "Gradianto Deep Ocean":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme");
                    break;
                case "Gradianto Midnight Blue":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme");
                    break;
                case "Gradianto Nature Green":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGradiantoNatureGreenIJTheme");
                    break;
                case "Gruvbox Dark Hard":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme");
                    break;
                case "Gruvbox Dark Medium":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkMediumIJTheme");
                    break;
                case "Gruvbox Dark Soft":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkSoftIJTheme");
                    break;
                case "Hiberbee Dark":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme");
                    break;
                case "High Contrast":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme");
                    break;
                case "Light Flat":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme");
                    break;
                case "Material Design Dark":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatMaterialDesignDarkIJTheme");
                    break;
                case "Monocai":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatMonocaiIJTheme");
                    break;
                case "Monokai Pro":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme");
                    break;
                case "Nord":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatNordIJTheme");
                    break;
                case "One Dark":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme");
                    break;
                case "Solarized Dark":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme");
                    break;
                case "Solarized Light":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme");
                    break;
                case "Spacegray":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme");
                    break;
                case "Vuesion":
                    UIManager.setLookAndFeel("com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme");
                    break;

                default:
                    FlatDarkLaf.setup();
            }
        } catch (Exception e) {
            // Fallback to dark theme if the selected theme is not available
            System.err.println("Failed to apply theme '" + themeName + "': " + e.getMessage());
            System.err.println("Note: IntelliJ themes require flatlaf-intellij-themes.jar in classpath");
            FlatDarkLaf.setup();
        }
    }
}