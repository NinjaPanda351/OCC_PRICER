package com.cardpricer.gui.panel.prefs;

import com.cardpricer.service.ScryfallCatalogService;
import com.cardpricer.util.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Card Catalog tab: download/load the Scryfall bulk catalog for instant paste-import.
 */
public final class CatalogTab {

    private JLabel       catalogStatusLabel;
    private JLabel       catalogAgeLabel;
    private JProgressBar catalogProgressBar;
    private JLabel       catalogProgressLabel;
    private JButton      downloadCatalogBtn;
    private JButton      loadCatalogBtn;
    private JButton      copyToSharedBtn;
    private JButton      loadFromSharedBtn;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Builds and returns the Card Catalog tab panel. */
    public JPanel build() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 10, 10, 10));

        // ── Status section ────────────────────────────────────────────────────
        JPanel statusSection = new JPanel(new GridBagLayout());
        statusSection.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Catalog Status"),
                new EmptyBorder(10, 12, 10, 12)));
        statusSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        statusSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 5, 4, 5);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        statusSection.add(new JLabel("Status:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        catalogStatusLabel = new JLabel("Checking\u2026");
        statusSection.add(catalogStatusLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        statusSection.add(new JLabel("Last updated:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        catalogAgeLabel = new JLabel("\u2014");
        catalogAgeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        statusSection.add(catalogAgeLabel, gbc);

        panel.add(statusSection);
        panel.add(Box.createVerticalStrut(10));

        // ── Description ───────────────────────────────────────────────────────
        JLabel descLabel = new JLabel(
                "<html>The card catalog downloads Scryfall\u2019s full card list (~30\u00a0MB download, "
                + "~15\u00a0MB saved to disk).<br>Once loaded, paste-import resolves all cards "
                + "instantly without individual API calls.</html>");
        descLabel.setFont(descLabel.getFont().deriveFont(Font.ITALIC, 11f));
        descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(descLabel);
        panel.add(Box.createVerticalStrut(12));

        // ── Buttons ───────────────────────────────────────────────────────────
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        downloadCatalogBtn = AppTheme.primaryButton("Download / Refresh Catalog");
        downloadCatalogBtn.addActionListener(e -> startCatalogDownload());

        loadCatalogBtn = new JButton("Load into Memory");
        loadCatalogBtn.setFocusPainted(false);
        loadCatalogBtn.addActionListener(e -> startCatalogLoad());

        copyToSharedBtn = new JButton("Copy to Shared Folder");
        copyToSharedBtn.setFocusPainted(false);
        copyToSharedBtn.setToolTipText("Copy local catalog to the shared folder so other workstations can import it");
        copyToSharedBtn.addActionListener(e -> copyToSharedFolder());

        loadFromSharedBtn = new JButton("Load from Shared Folder");
        loadFromSharedBtn.setFocusPainted(false);
        loadFromSharedBtn.setToolTipText("Copy catalog from shared folder to local cache, then load into memory");
        loadFromSharedBtn.addActionListener(e -> loadFromSharedFolder());

        btnRow.add(downloadCatalogBtn);
        btnRow.add(loadCatalogBtn);
        btnRow.add(copyToSharedBtn);
        btnRow.add(loadFromSharedBtn);
        panel.add(btnRow);
        panel.add(Box.createVerticalStrut(10));

        // ── Progress area (hidden until an operation starts) ──────────────────
        JPanel progressSection = new JPanel(new BorderLayout(0, 4));
        progressSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        catalogProgressLabel = new JLabel(" ");
        catalogProgressLabel.setFont(catalogProgressLabel.getFont().deriveFont(Font.ITALIC, 11f));
        catalogProgressBar   = new JProgressBar(0, 100);
        catalogProgressBar.setStringPainted(true);
        catalogProgressBar.setIndeterminate(true);
        catalogProgressBar.setVisible(false);
        catalogProgressLabel.setVisible(false);

        progressSection.add(catalogProgressLabel, BorderLayout.NORTH);
        progressSection.add(catalogProgressBar,   BorderLayout.CENTER);
        panel.add(progressSection);
        panel.add(Box.createVerticalGlue());

        refreshCatalogStatus();
        return panel;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void refreshCatalogStatus() {
        ScryfallCatalogService catalog = ScryfallCatalogService.getInstance();

        if (catalog.isLoaded()) {
            catalogStatusLabel.setText("Loaded in memory \u2014 "
                    + String.format("%,d", catalog.getCardCount()) + " cards");
            catalogStatusLabel.setForeground(AppTheme.SUCCESS);
        } else if (catalog.isCatalogAvailable()) {
            catalogStatusLabel.setText("Available on disk \u2014 not yet loaded into memory");
            catalogStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
        } else {
            catalogStatusLabel.setText("Not downloaded");
            catalogStatusLabel.setForeground(AppTheme.DANGER);
        }

        if (catalog.isCatalogAvailable()) {
            long ageMs = catalog.getCacheAgeMs();
            long days  = ageMs / 86_400_000L;
            long hours = (ageMs % 86_400_000L) / 3_600_000L;
            if (days > 0) {
                catalogAgeLabel.setText(days + " day" + (days == 1 ? "" : "s") + " ago");
            } else {
                catalogAgeLabel.setText(hours + " hour" + (hours == 1 ? "" : "s") + " ago");
            }
            catalogAgeLabel.setForeground(days >= 7
                    ? new Color(0xD97706) : UIManager.getColor("Label.disabledForeground"));
        } else {
            catalogAgeLabel.setText("Never");
        }

        loadCatalogBtn.setVisible(catalog.isCatalogAvailable() && !catalog.isLoaded());

        boolean hasShared = !NetworkTab.getSharedTradesFolder().isEmpty();
        copyToSharedBtn.setVisible(hasShared && catalog.isCatalogAvailable());
        loadFromSharedBtn.setVisible(hasShared && catalog.isSharedCatalogAvailable());
    }

    private void startCatalogDownload() {
        setAllButtonsEnabled(false);
        catalogProgressBar.setIndeterminate(false);
        catalogProgressBar.setValue(0);
        catalogProgressBar.setVisible(true);
        catalogProgressLabel.setVisible(true);
        catalogProgressLabel.setText("Starting download\u2026");

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                java.util.function.BooleanSupplier cancelCheck = this::isCancelled;
                ScryfallCatalogService.getInstance().downloadAndBuild(
                        new ScryfallCatalogService.DownloadProgress() {
                            @Override
                            public void onUpdate(int cardsProcessed, String phase) {
                                publish(cardsProcessed + "\t" + phase);
                            }
                            @Override
                            public boolean isCancelled() {
                                return cancelCheck.getAsBoolean();
                            }
                        });
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                String last = chunks.get(chunks.size() - 1);
                String[] parts = last.split("\t", 2);
                try {
                    int count = Integer.parseInt(parts[0]);
                    catalogProgressLabel.setText(parts.length > 1 ? parts[1] : " ");
                    if (count > 0 && !catalogProgressBar.isIndeterminate()) {
                        int pct = Math.min(99, count * 100 / 300_000);
                        catalogProgressBar.setValue(pct);
                    }
                } catch (NumberFormatException ignored) {}
            }

            @Override
            protected void done() {
                catalogProgressBar.setValue(100);
                try {
                    get();
                    catalogProgressLabel.setText("Catalog downloaded and ready.");
                    catalogProgressLabel.setForeground(AppTheme.SUCCESS);
                } catch (java.util.concurrent.CancellationException ignored) {
                    catalogProgressLabel.setText("Download cancelled.");
                    catalogProgressLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    catalogProgressLabel.setText("Error: " + msg);
                    catalogProgressLabel.setForeground(AppTheme.DANGER);
                }
                setAllButtonsEnabled(true);
                refreshCatalogStatus();
            }
        }.execute();
    }

    private void copyToSharedFolder() {
        setAllButtonsEnabled(false);
        catalogProgressBar.setIndeterminate(true);
        catalogProgressBar.setVisible(true);
        catalogProgressLabel.setVisible(true);
        catalogProgressLabel.setForeground(UIManager.getColor("Label.foreground"));
        catalogProgressLabel.setText("Copying catalog to shared folder\u2026");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ScryfallCatalogService.getInstance().copyCatalogToSharedFolder();
                return null;
            }
            @Override
            protected void done() {
                catalogProgressBar.setIndeterminate(false);
                catalogProgressBar.setValue(100);
                try {
                    get();
                    catalogProgressLabel.setText("Catalog copied to shared folder.");
                    catalogProgressLabel.setForeground(AppTheme.SUCCESS);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    catalogProgressLabel.setText("Copy failed: " + msg);
                    catalogProgressLabel.setForeground(AppTheme.DANGER);
                }
                setAllButtonsEnabled(true);
                refreshCatalogStatus();
            }
        }.execute();
    }

    private void loadFromSharedFolder() {
        setAllButtonsEnabled(false);
        catalogProgressBar.setIndeterminate(true);
        catalogProgressBar.setVisible(true);
        catalogProgressLabel.setVisible(true);
        catalogProgressLabel.setForeground(UIManager.getColor("Label.foreground"));
        catalogProgressLabel.setText("Importing catalog from shared folder\u2026");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return ScryfallCatalogService.getInstance().importCatalogFromSharedFolder();
            }
            @Override
            protected void done() {
                catalogProgressBar.setIndeterminate(false);
                catalogProgressBar.setValue(100);
                try {
                    int count = get();
                    catalogProgressLabel.setText("Loaded " + String.format("%,d", count) + " cards from shared folder.");
                    catalogProgressLabel.setForeground(AppTheme.SUCCESS);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    catalogProgressLabel.setText("Import failed: " + msg);
                    catalogProgressLabel.setForeground(AppTheme.DANGER);
                }
                setAllButtonsEnabled(true);
                refreshCatalogStatus();
            }
        }.execute();
    }

    private void setAllButtonsEnabled(boolean enabled) {
        downloadCatalogBtn.setEnabled(enabled);
        loadCatalogBtn.setEnabled(enabled);
        copyToSharedBtn.setEnabled(enabled);
        loadFromSharedBtn.setEnabled(enabled);
    }

    private void startCatalogLoad() {
        setAllButtonsEnabled(false);
        catalogProgressBar.setIndeterminate(true);
        catalogProgressBar.setVisible(true);
        catalogProgressLabel.setVisible(true);
        catalogProgressLabel.setForeground(UIManager.getColor("Label.foreground"));
        catalogProgressLabel.setText("Loading catalog from disk\u2026");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return ScryfallCatalogService.getInstance().loadFromDisk();
            }
            @Override
            protected void done() {
                catalogProgressBar.setIndeterminate(false);
                catalogProgressBar.setValue(100);
                try {
                    int count = get();
                    catalogProgressLabel.setText("Loaded " + String.format("%,d", count) + " cards.");
                    catalogProgressLabel.setForeground(AppTheme.SUCCESS);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    catalogProgressLabel.setText("Load failed: " + msg);
                    catalogProgressLabel.setForeground(AppTheme.DANGER);
                }
                setAllButtonsEnabled(true);
                refreshCatalogStatus();
            }
        }.execute();
    }
}
