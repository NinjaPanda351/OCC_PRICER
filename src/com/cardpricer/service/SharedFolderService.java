package com.cardpricer.service;

import com.cardpricer.gui.panel.PreferencesPanel;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Utility service for copying files to the configured shared trades folder.
 *
 * <p>All operations are best-effort: failures are logged but never propagated.
 */
public final class SharedFolderService {

    private SharedFolderService() {}

    /**
     * Returns the accessible shared trades folder, or {@code null} if none is configured
     * or the path is not reachable.
     */
    public static File resolveSharedDirectory() {
        String path = PreferencesPanel.getSharedTradesFolder();
        if (path == null || path.isBlank()) return null;
        File dir = new File(path);
        return (dir.exists() && dir.isDirectory()) ? dir : null;
    }

    /**
     * Copies {@code localFilePath} to the shared trades folder (if configured and reachable).
     * Failures are logged to stderr but never thrown.
     */
    public static void copyToSharedFolder(String localFilePath) {
        try {
            File sharedDir = resolveSharedDirectory();
            if (sharedDir == null) return;
            File src  = new File(localFilePath);
            File dest = new File(sharedDir, src.getName());
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Copied to shared folder: " + dest.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[SharedFolder] Failed to copy file: " + e.getMessage());
        }
    }
}
