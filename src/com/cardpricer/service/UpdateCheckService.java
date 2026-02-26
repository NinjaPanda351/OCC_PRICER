package com.cardpricer.service;

import com.cardpricer.util.AppVersion;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Consumer;

/**
 * Checks GitHub Releases for a newer version of OCC Card Pricer on a daemon thread
 * so it never blocks the UI at startup.
 *
 * <p>When a newer version is found the supplied {@code onUpdateAvailable} callback
 * receives a ready-to-display banner {@link JPanel} that can be added to the main
 * frame.  The banner contains a "Download" button (opens the browser to the releases
 * page) and a dismiss "✕" button.
 *
 * <p>No silent self-patching is performed — the user always manually downloads and
 * installs the new release.
 */
public class UpdateCheckService {

    private static final String API_URL =
            "https://api.github.com/repos/" + AppVersion.GITHUB_OWNER
            + "/" + AppVersion.GITHUB_REPO + "/releases/latest";

    private static final String RELEASES_PAGE =
            "https://github.com/" + AppVersion.GITHUB_OWNER
            + "/" + AppVersion.GITHUB_REPO + "/releases/latest";

    /**
     * Starts the update check on a background daemon thread.
     *
     * @param onUpdateAvailable called on the EDT when a newer version is found;
     *                          receives the banner panel to display
     */
    public static void checkAsync(Consumer<JPanel> onUpdateAvailable) {
        Thread thread = new Thread(() -> {
            try {
                String latestTag = fetchLatestTag();
                if (latestTag == null) return;

                String latestVersion = latestTag.startsWith("v")
                        ? latestTag.substring(1)
                        : latestTag;

                if (isNewer(latestVersion, AppVersion.CURRENT)) {
                    JPanel banner = buildBanner(latestVersion);
                    SwingUtilities.invokeLater(() -> onUpdateAvailable.accept(banner));
                }
            } catch (Exception e) {
                // Network errors are silently ignored — update check is best-effort
                System.err.println("[UpdateCheck] Check failed: " + e.getMessage());
            }
        }, "update-check-thread");
        thread.setDaemon(true);
        thread.start();
    }

    /** Fetches the tag_name of the latest release via the GitHub API. */
    private static String fetchLatestTag() throws Exception {
        URL url = new URI(API_URL).toURL();
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(5_000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "OCC-Card-Pricer/" + AppVersion.CURRENT);

        try (InputStream in = conn.getInputStream()) {
            String json = new String(in.readAllBytes());
            // Minimal JSON parse — extract "tag_name":"v2.0.8"
            int idx = json.indexOf("\"tag_name\"");
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            int q1    = json.indexOf('"', colon + 1);
            int q2    = json.indexOf('"', q1 + 1);
            return json.substring(q1 + 1, q2);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Compares two semantic-version strings (major.minor.patch).
     *
     * @return {@code true} if {@code candidate} is strictly newer than {@code current}
     */
    static boolean isNewer(String candidate, String current) {
        try {
            int[] c = parse(candidate);
            int[] x = parse(current);
            for (int i = 0; i < 3; i++) {
                if (c[i] > x[i]) return true;
                if (c[i] < x[i]) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] parse(String v) {
        String[] parts = v.split("\\.");
        int[] nums = new int[3];
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            nums[i] = Integer.parseInt(parts[i].trim());
        }
        return nums;
    }

    /** Builds the dismissible update-available banner panel. */
    private static JPanel buildBanner(String newVersion) {
        JPanel banner = new JPanel(new BorderLayout(8, 0));
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                        new Color(0x4A, 0x90, 0xD9)),
                BorderFactory.createEmptyBorder(6, 12, 6, 8)
        ));
        banner.setBackground(new Color(0x1E, 0x3A, 0x5F));

        JLabel msg = new JLabel("Version " + newVersion + " is available.");
        msg.setForeground(Color.WHITE);

        JButton downloadBtn = new JButton("Download");
        downloadBtn.setFocusPainted(false);
        downloadBtn.addActionListener(e -> openBrowser());

        JButton dismissBtn = new JButton("✕");
        dismissBtn.setToolTipText("Dismiss");
        dismissBtn.setFocusPainted(false);
        dismissBtn.setBorderPainted(false);
        dismissBtn.setContentAreaFilled(false);
        dismissBtn.setForeground(new Color(0xCC, 0xCC, 0xCC));
        dismissBtn.addActionListener(e -> {
            Container parent = banner.getParent();
            if (parent != null) {
                parent.remove(banner);
                parent.revalidate();
                parent.repaint();
            }
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(downloadBtn);
        right.add(dismissBtn);

        banner.add(msg, BorderLayout.CENTER);
        banner.add(right, BorderLayout.EAST);
        return banner;
    }

    private static void openBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(RELEASES_PAGE));
            }
        } catch (Exception e) {
            System.err.println("[UpdateCheck] Failed to open browser: " + e.getMessage());
        }
    }
}
