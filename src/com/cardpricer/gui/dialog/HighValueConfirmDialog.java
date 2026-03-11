package com.cardpricer.gui.dialog;

import com.cardpricer.model.Card;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.net.URL;

/**
 * Modal dialog that asks the user to verify a high-value card before adding it to a trade.
 *
 * <p>Usage:
 * <pre>
 *     boolean confirmed = HighValueConfirmDialog.show(parentWindow, card, price);
 * </pre>
 */
public final class HighValueConfirmDialog {

    private HighValueConfirmDialog() {}

    /**
     * Shows the dialog and blocks until the user confirms or cancels.
     *
     * @param parent the owner window (for centering)
     * @param card   the card about to be added
     * @param price  the computed rounded price
     * @return {@code true} if the user confirmed; {@code false} to cancel
     */
    public static boolean show(Window parent, Card card, BigDecimal price) {
        JDialog dialog = new JDialog(parent,
                "Verify High-Value Card", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel main = new JPanel(new BorderLayout(14, 10));
        main.setBorder(new EmptyBorder(16, 16, 12, 16));

        // ── Left: card image ──────────────────────────────────────────────────
        int imgW = 200;
        int imgH = (int) (imgW * 1.396);
        JLabel imgLabel = new JLabel("<html><center style='color:gray;'>Loading…</center></html>",
                SwingConstants.CENTER);
        imgLabel.setPreferredSize(new Dimension(imgW, imgH));
        imgLabel.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));

        if (card.getImageUrl() != null) {
            final String imgUrl = card.getImageUrl();
            new SwingWorker<ImageIcon, Void>() {
                @Override protected ImageIcon doInBackground() throws Exception {
                    java.awt.image.BufferedImage raw = ImageIO.read(new URL(imgUrl));
                    if (raw == null) return null;
                    int h = raw.getHeight() * imgW / raw.getWidth();
                    return new ImageIcon(raw.getScaledInstance(imgW, h, java.awt.Image.SCALE_SMOOTH));
                }
                @Override protected void done() {
                    try {
                        ImageIcon icon = get();
                        if (icon != null) {
                            imgLabel.setIcon(icon);
                            imgLabel.setText(null);
                            imgLabel.setPreferredSize(null);
                            dialog.pack();
                        }
                    } catch (Exception ignored) {}
                }
            }.execute();
        }

        // ── Centre: card info ─────────────────────────────────────────────────
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(0, 10, 0, 0));

        JLabel warnLabel = new JLabel("High-Value Card — Please Verify");
        warnLabel.setFont(warnLabel.getFont().deriveFont(Font.BOLD, 13f));
        warnLabel.setForeground(new Color(180, 100, 0));

        JLabel nameLabel = new JLabel(card.getName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 18f));

        String setInfo = card.getSetCode() + "  #" + card.getCollectorNumber();
        if (card.isReserved()) setInfo += "  [Reserved List]";
        JLabel setLabel = new JLabel(setInfo);
        setLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JLabel priceLabel = new JLabel(String.format("Market Value:  $%.2f", price));
        priceLabel.setFont(priceLabel.getFont().deriveFont(Font.BOLD, 22f));
        priceLabel.setForeground(new Color(0, 140, 0));

        JLabel promptLabel = new JLabel(
                "<html>Physically verify the card before adding it to this trade.</html>");
        promptLabel.setFont(promptLabel.getFont().deriveFont(Font.PLAIN, 12f));

        info.add(warnLabel);
        info.add(Box.createVerticalStrut(10));
        info.add(nameLabel);
        info.add(Box.createVerticalStrut(4));
        info.add(setLabel);
        info.add(Box.createVerticalStrut(14));
        info.add(priceLabel);
        info.add(Box.createVerticalStrut(14));
        info.add(promptLabel);

        // ── Bottom: buttons ───────────────────────────────────────────────────
        boolean[] confirmed = {false};

        JButton addBtn = new JButton("Add to Trade");
        addBtn.setFocusPainted(false);
        addBtn.putClientProperty("JButton.buttonType", "roundRect");
        addBtn.addActionListener(e -> { confirmed[0] = true; dialog.dispose(); });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setFocusPainted(false);
        cancelBtn.putClientProperty("JButton.buttonType", "roundRect");
        cancelBtn.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelBtn);
        buttons.add(addBtn);

        main.add(imgLabel,  BorderLayout.WEST);
        main.add(info,      BorderLayout.CENTER);
        main.add(buttons,   BorderLayout.SOUTH);

        dialog.setContentPane(main);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(500, 300));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true); // blocks until closed (modal)

        return confirmed[0];
    }
}
