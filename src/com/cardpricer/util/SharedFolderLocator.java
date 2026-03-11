package com.cardpricer.util;

import java.io.File;
import java.util.prefs.Preferences;

/**
 * Resolves the configuration directory used for JSON settings files
 * (buy rates, bounties, custom sets).
 *
 * <p>When a shared folder is configured and reachable, the {@code config/}
 * subdirectory of that shared folder is returned — so all workstations read
 * and write the same settings files.  Falls back to the local
 * {@link AppDataDirectory#config()} when no shared folder is set or the
 * folder is not accessible.
 *
 * <p>The shared folder path is read from the same {@link Preferences} node
 * that {@code AppearanceTab} / {@code NetworkTab} write to.
 */
public final class SharedFolderLocator {

    // Node path matching Preferences.userNodeForPackage(PreferencesPanel.class)
    private static final Preferences PREFS =
            Preferences.userRoot().node("com/cardpricer/gui/panel");
    static final String FOLDER_KEY = "shared.trades.folder";

    private SharedFolderLocator() {}

    /** Returns the configured shared trades folder path, or empty string. */
    public static String getSharedFolder() {
        return PREFS.get(FOLDER_KEY, "");
    }

    /**
     * Returns the config directory for JSON settings files.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@code <sharedFolder>/config/} — if shared folder is set and writable</li>
     *   <li>{@link AppDataDirectory#config()} — local AppData fallback</li>
     * </ol>
     */
    public static File configDir() {
        String shared = getSharedFolder();
        if (!shared.isBlank()) {
            File root = new File(shared);
            if (root.isDirectory()) {
                File cfg = new File(root, "config");
                cfg.mkdirs();
                if (cfg.isDirectory() && cfg.canWrite()) {
                    return cfg;
                }
            }
        }
        return AppDataDirectory.config();
    }
}
