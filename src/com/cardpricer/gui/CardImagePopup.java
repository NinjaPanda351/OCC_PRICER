package com.cardpricer.gui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Floating popup that displays a Scryfall card image near the mouse cursor.
 * Images are fetched asynchronously and cached (LRU, max 20 entries).
 */
public class CardImagePopup {

    private static final int DISPLAY_WIDTH = 230;
    private static final int MAX_CACHE_SIZE = 20;

    private final JWindow popup;
    private final JLabel imageLabel;

    private SwingWorker<ImageIcon, Void> loader;
    private String currentUrl;

    /** LRU image cache keyed by URL. */
    private final Map<String, ImageIcon> cache = new LinkedHashMap<>(MAX_CACHE_SIZE + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    /**
     * Creates a new card-image popup owned by the given window.
     *
     * @param owner the parent window used for the floating {@link JWindow}
     */
    public CardImagePopup(Window owner) {
        popup = new JWindow(owner);
        popup.setAlwaysOnTop(true);

        imageLabel = new JLabel();
        imageLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        popup.getContentPane().add(imageLabel);
    }

    /**
     * Shows the popup for the given image URL near {@code screenPos}.
     * Pass {@code null} to hide the popup.
     */
    public void show(String imageUrl, Point screenPos) {
        if (imageUrl == null) {
            hide();
            return;
        }

        // Already showing the same card — just reposition
        if (imageUrl.equals(currentUrl) && popup.isVisible()) {
            movePopup(screenPos);
            return;
        }

        currentUrl = imageUrl;
        cancelLoader();

        ImageIcon cached = cache.get(imageUrl);
        if (cached != null) {
            displayIcon(cached, screenPos);
            return;
        }

        // Show a placeholder while the image loads
        imageLabel.setIcon(null);
        imageLabel.setText("<html><div style='width:" + DISPLAY_WIDTH + "px;text-align:center;"
                + "padding:40px 0;color:gray;'>Loading...</div></html>");
        imageLabel.setPreferredSize(new Dimension(DISPLAY_WIDTH, (int) (DISPLAY_WIDTH * 1.396)));
        popup.pack();
        movePopup(screenPos);
        popup.setVisible(true);

        final String urlToLoad = imageUrl;
        loader = new SwingWorker<>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                BufferedImage img = ImageIO.read(new URL(urlToLoad));
                if (img == null || isCancelled()) return null;
                int h = img.getHeight() * DISPLAY_WIDTH / img.getWidth();
                Image scaled = img.getScaledInstance(DISPLAY_WIDTH, h, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    ImageIcon icon = get();
                    if (icon != null && urlToLoad.equals(currentUrl)) {
                        cache.put(urlToLoad, icon);
                        displayIcon(icon, null); // keep current position
                    }
                } catch (InterruptedException | ExecutionException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        loader.execute();
    }

    /** Hides the popup and cancels any in-progress image load. */
    public void hide() {
        currentUrl = null;
        popup.setVisible(false);
        cancelLoader();
    }

    private void displayIcon(ImageIcon icon, Point screenPos) {
        imageLabel.setIcon(icon);
        imageLabel.setText(null);
        imageLabel.setPreferredSize(null);
        popup.pack();
        if (screenPos != null) movePopup(screenPos);
        popup.setVisible(true);
    }

    private void movePopup(Point screenPos) {
        int x = screenPos.x + 20;
        int y = screenPos.y + 10;
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        if (x + popup.getWidth() > screen.width)  x = screenPos.x - popup.getWidth() - 5;
        if (y + popup.getHeight() > screen.height) y = screenPos.y - popup.getHeight() - 5;
        popup.setLocation(x, y);
    }

    private void cancelLoader() {
        if (loader != null && !loader.isDone()) {
            loader.cancel(true);
        }
    }
}
