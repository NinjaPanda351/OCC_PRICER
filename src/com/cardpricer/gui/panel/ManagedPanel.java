package com.cardpricer.gui.panel;

/**
 * Implemented by panels that participate in smart memory management.
 * MainSwingApplication queries this before unloading a panel from memory.
 */
public interface ManagedPanel {

    /**
     * Returns true if this panel can be safely removed from the component tree.
     * Panels with in-progress async work or unsaved state should return false.
     *
     * @return {@code true} if the panel may be unloaded without data loss or interruption
     */
    boolean isSafeToUnload();
}
