package com.cardpricer.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Resolves the persistent user-data directory for the application.
 *
 * <p>On Windows the root is {@code %APPDATA%\OCC_Trade_Pricer\}; on other platforms
 * it falls back to {@code ~/.occ_trade_pricer/}.  This keeps user data completely
 * separate from the app-image folder, so extracting a new release ZIP never
 * touches trades, prices, or inventory files.
 *
 * <p>On the very first launch after an update (detected by the new root directory
 * being empty), any existing {@code data/} folder found beside the JAR is
 * automatically migrated to the new location.
 */
public class AppDataDirectory {

    private static final String APP_NAME = "OCC_Trade_Pricer";

    // Resolved once at class-load time
    private static final File ROOT = resolveRoot();

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    /** Returns the root user-data directory, creating it if absent. */
    public static File root()          { return ROOT; }
    /** Returns the {@code trades} subdirectory, creating it if absent. */
    public static File trades()        { return subdir("trades"); }
    /** Returns the {@code prices} subdirectory, creating it if absent. */
    public static File prices()        { return subdir("prices"); }
    /** Returns the {@code inventory} subdirectory, creating it if absent. */
    public static File inventory()     { return subdir("inventory"); }
    /** Returns the {@code combined_files} subdirectory, creating it if absent. */
    public static File combinedFiles() { return subdir("combined_files"); }
    /** Returns the {@code cache} subdirectory, creating it if absent. */
    public static File cache()         { return subdir("cache"); }

    /** Returns the absolute path string for the {@code trades} sub-folder. */
    public static String tradesPath()        { return trades().getAbsolutePath(); }
    /** Returns the absolute path string for the {@code prices} sub-folder. */
    public static String pricesPath()        { return prices().getAbsolutePath(); }
    /** Returns the absolute path string for the {@code inventory} sub-folder. */
    public static String inventoryPath()     { return inventory().getAbsolutePath(); }
    /** Returns the absolute path string for the {@code combined_files} sub-folder. */
    public static String combinedFilesPath() { return combinedFiles().getAbsolutePath(); }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static File resolveRoot() {
        File root;
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            root = new File(appData, APP_NAME);
        } else {
            root = new File(System.getProperty("user.home"), "." + APP_NAME.toLowerCase());
        }
        root.mkdirs();
        migrateIfNeeded(root);
        return root;
    }

    private static File subdir(String name) {
        File dir = new File(ROOT, name);
        dir.mkdirs();
        return dir;
    }

    /**
     * One-time migration: if the new root is empty and an old {@code data/}
     * directory exists beside the application JAR, copies all files across.
     * Silently ignores any errors — migration is best-effort.
     */
    private static void migrateIfNeeded(File newRoot) {
        // Skip if the root already has content (already migrated or fresh install)
        String[] existing = newRoot.list();
        if (existing != null && existing.length > 0) return;

        try {
            // Locate old data/ dir relative to the JAR
            java.net.URL loc = AppDataDirectory.class
                    .getProtectionDomain().getCodeSource().getLocation();
            File base = new File(loc.toURI());
            if (!base.isDirectory()) base = base.getParentFile();
            File oldData = new File(base, "data");
            if (!oldData.exists() || !oldData.isDirectory()) return;

            // Copy each subdirectory
            File[] subdirs = oldData.listFiles(File::isDirectory);
            if (subdirs == null) return;
            for (File sub : subdirs) {
                File dest = new File(newRoot, sub.getName());
                dest.mkdirs();
                File[] files = sub.listFiles(File::isFile);
                if (files == null) continue;
                for (File f : files) {
                    Files.copy(f.toPath(), new File(dest, f.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            System.out.println("Migrated existing data from " + oldData + " → " + newRoot);
        } catch (Exception ignored) {
            // Never crash the app over a migration failure
        }
    }
}
