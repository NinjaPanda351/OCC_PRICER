package com.cardpricer.gui.panel.trade;

import com.cardpricer.service.TradeSessionService;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages the 60-second autosave timer and crash-recovery restore flow.
 *
 * <p>Construction requires two collaborators supplied by {@code TradePanel}:
 * <ul>
 *   <li>{@code sessionReader} — returns the current table state as a
 *       {@link TradeSessionService.SavedSession}, or {@code null} when the table is empty.</li>
 *   <li>{@code sessionWriter} — applies a loaded session back onto the trade table.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *     autosaveController = new TradeAutosaveController(
 *             this::buildCurrentSession,
 *             this::restoreSession,
 *             this::getParentWindow);
 *     SwingUtilities.invokeLater(autosaveController::offerRestore);
 *     autosaveController.start();
 * </pre>
 */
public final class TradeAutosaveController {

    private final Supplier<TradeSessionService.SavedSession> sessionReader;
    private final Consumer<TradeSessionService.SavedSession> sessionWriter;
    private final Supplier<Window> parentWindow;

    private Timer autosaveTimer;

    /**
     * @param sessionReader  provides the current session state for autosave
     * @param sessionWriter  restores a session onto the trade table
     * @param parentWindow   owner window supplier for dialog centering
     */
    public TradeAutosaveController(
            Supplier<TradeSessionService.SavedSession> sessionReader,
            Consumer<TradeSessionService.SavedSession> sessionWriter,
            Supplier<Window> parentWindow) {
        this.sessionReader = sessionReader;
        this.sessionWriter = sessionWriter;
        this.parentWindow  = parentWindow;
    }

    /** Starts the 60-second periodic autosave timer. */
    public void start() {
        autosaveTimer = new Timer(60_000, e -> performAutosave());
        autosaveTimer.setRepeats(true);
        autosaveTimer.start();
    }

    /**
     * Checks for an existing autosave file and, if found, offers the user a choice to
     * restore it. If the user declines, the autosave file is deleted.
     *
     * <p>This should be called via {@code SwingUtilities.invokeLater} so the parent
     * window is fully realised before showing the dialog.
     */
    public void offerRestore() {
        if (!TradeSessionService.hasAutosave()) return;
        int choice = JOptionPane.showConfirmDialog(parentWindow.get(),
                "An unsaved trade session was found.\nWould you like to restore it?",
                "Restore Session",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            TradeSessionService.SavedSession session = TradeSessionService.load();
            if (session != null) sessionWriter.accept(session);
        } else {
            TradeSessionService.clearAutosave();
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void performAutosave() {
        TradeSessionService.SavedSession session = sessionReader.get();
        if (session == null || session.rows().isEmpty()) {
            TradeSessionService.clearAutosave();
        } else {
            TradeSessionService.save(session.traderName(), session.customerName(), session.rows());
        }
    }
}
