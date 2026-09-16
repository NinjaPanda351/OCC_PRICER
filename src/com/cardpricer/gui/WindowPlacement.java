package com.cardpricer.gui;

import java.awt.Rectangle;

/** Restores window geometry inside the current monitor's usable area. */
public final class WindowPlacement {
    private WindowPlacement() {}
    public static Rectangle fit(Rectangle requested, Rectangle usable) {
        int width = Math.min(Math.max(900, requested.width), usable.width);
        int height = Math.min(Math.max(600, requested.height), usable.height);
        int x = Math.max(usable.x, Math.min(requested.x, usable.x + usable.width - width));
        int y = Math.max(usable.y, Math.min(requested.y, usable.y + usable.height - height));
        return new Rectangle(x, y, width, height);
    }

    public static Rectangle fitToScreens(Rectangle requested, java.util.List<Rectangle> screens) {
        if (screens.isEmpty()) throw new IllegalArgumentException("At least one screen is required");
        Rectangle best = screens.getFirst();
        long largestOverlap = -1;
        for (Rectangle screen : screens) {
            Rectangle overlap = requested.intersection(screen);
            long area = (long)Math.max(0, overlap.width) * Math.max(0, overlap.height);
            if (area > largestOverlap) { largestOverlap = area; best = screen; }
        }
        return fit(requested, best);
    }
}
