package com.cardpricer.gui.dialog;

import com.cardpricer.service.BuyRateService;
import com.cardpricer.service.RateConfigurationRepository;
import com.cardpricer.service.TaskCoordinator;
import org.json.JSONObject;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Reviews complete saved rates and bounties before an explicit, revision-checked replacement. */
public final class RateConflictDialog {
    private RateConflictDialog() {}

    public static void show(Component owner, BuyRateService service, Runnable updated) {
        run(owner, "Reading local and shared rates…", service::reviewSharedRates, review -> {
            JPanel versions = new JPanel(new GridLayout(1, 2, 12, 0));
            versions.add(version("Saved on this computer", review.localDocument()));
            versions.add(version("Saved in shared folder", review.sharedDocument()));
            JPanel content = new JPanel(new BorderLayout(8, 8));
            content.add(new JLabel("Choose the complete saved version to use on both sides. Previous files are retained."), BorderLayout.NORTH);
            content.add(versions);
            content.add(new JLabel("Unsaved table edits are not included. Both rules and bounties will reload after applying."), BorderLayout.SOUTH);
            int choice = JOptionPane.showOptionDialog(owner, content, "Review shared rates", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, new String[]{"Use this computer's version", "Use shared version", "Cancel"}, "Cancel");
            if (choice != 0 && choice != 1) return;
            var selected = choice == 0 ? RateConfigurationRepository.Choice.LOCAL : RateConfigurationRepository.Choice.SHARED;
            run(owner, "Applying selected rates…", () -> { service.resolveSharedRates(review, selected); return true; }, ignored -> updated.run());
        });
    }

    private static JPanel version(String title, String document) {
        JTextArea text = new JTextArea(18, 36);
        text.setEditable(false);
        try {
            if (document.isBlank()) text.setText("No saved version");
            else {
                JSONObject data = new JSONObject(document);
                StringBuilder summary = new StringBuilder("Revision: ").append(data.optString("revision", "legacy / unversioned"))
                        .append("\n\nMinimum value / Credit rate / Check rate\n");
                var rules = data.optJSONArray("rules");
                if (rules != null) for (Object value : rules) {
                    JSONObject row = (JSONObject)value;
                    summary.append(row.opt("thresholdMin")).append(" / ").append(row.opt("creditRate")).append(" / ").append(row.opt("checkRate")).append('\n');
                }
                summary.append("\nBounties / Credit rate / Check rate\n");
                var bounties = data.optJSONArray("bounties");
                if (bounties != null) for (Object value : bounties) {
                    JSONObject row = (JSONObject)value;
                    summary.append(row.opt("cardName")).append(" / ").append(row.opt("creditRate")).append(" / ").append(row.opt("checkRate")).append('\n');
                }
                text.setText(summary.toString());
            }
        } catch (RuntimeException failure) { text.setText("Invalid saved document: " + failure.getMessage()); }
        text.setCaretPosition(0);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(text));
        return panel;
    }

    private static <T> void run(Component owner, String message, Callable<T> task, Consumer<T> success) {
        Window window = owner instanceof Window w ? w : SwingUtilities.getWindowAncestor(owner);
        JDialog progress = new JDialog(window, "Shared rates", Dialog.ModalityType.APPLICATION_MODAL);
        progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        progress.add(new JLabel(message, SwingConstants.CENTER));
        progress.setSize(380, 100);
        progress.setLocationRelativeTo(owner);
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            protected T doInBackground() throws Exception { return task.call(); }
            protected void done() {
                progress.dispose();
                try { success.accept(get()); }
                catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    JOptionPane.showMessageDialog(owner, cause.getMessage(), "Rates were not applied", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        TaskCoordinator.execute(worker);
        progress.setVisible(true);
    }
}
