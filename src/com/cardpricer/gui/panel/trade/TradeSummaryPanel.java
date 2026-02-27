package com.cardpricer.gui.panel.trade;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Displays the running trade total and payout rates (50%, 33.33%).
 * Call update() after every table change.
 */
public class TradeSummaryPanel extends JPanel {

    private final JLabel totalPriceLabel;
    private final JLabel halfRateLabel;
    private final JLabel thirdRateLabel;

    /** Constructs the summary panel and initialises all value labels to zero. */
    public TradeSummaryPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Value Summary"),
                new EmptyBorder(10, 15, 10, 15)
        ));
        setPreferredSize(new Dimension(280, 120));

        totalPriceLabel = new JLabel("TOTAL: $0.00 (0 cards)");
        totalPriceLabel.setFont(totalPriceLabel.getFont().deriveFont(Font.BOLD, 18f));
        totalPriceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        halfRateLabel = new JLabel("HALF RATE (50%): $0.00");
        halfRateLabel.setFont(halfRateLabel.getFont().deriveFont(Font.PLAIN, 14f));
        halfRateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        thirdRateLabel = new JLabel("THIRD RATE (33.33%): $0.00");
        thirdRateLabel.setFont(thirdRateLabel.getFont().deriveFont(Font.PLAIN, 14f));
        thirdRateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        add(totalPriceLabel);
        add(Box.createVerticalStrut(8));
        add(halfRateLabel);
        add(Box.createVerticalStrut(5));
        add(thirdRateLabel);
    }

    /**
     * Refreshes all labels and highlights the rate that matches paymentType.
     *
     * @param total       total market value of all cards in the trade
     * @param totalQty    total card count across all rows
     * @param paymentType "credit", "check", "inventory", or "partial"
     */
    public void update(BigDecimal total, int totalQty, String paymentType) {
        BigDecimal halfRate = total.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal thirdRate = total.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);

        totalPriceLabel.setText(String.format("TOTAL: $%.2f (%d cards)", total, totalQty));
        halfRateLabel.setText(String.format("HALF RATE (50%%): $%.2f", halfRate));
        thirdRateLabel.setText(String.format("THIRD RATE (33.33%%): $%.2f", thirdRate));

        highlightPaymentRate(paymentType);
    }

    private void highlightPaymentRate(String paymentType) {
        // Reset both to normal
        halfRateLabel.setFont(halfRateLabel.getFont().deriveFont(Font.PLAIN, 14f));
        thirdRateLabel.setFont(thirdRateLabel.getFont().deriveFont(Font.PLAIN, 14f));
        halfRateLabel.setForeground(UIManager.getColor("Label.foreground"));
        thirdRateLabel.setForeground(UIManager.getColor("Label.foreground"));

        // Highlight the selected rate (none for inventory or partial mode)
        if ("credit".equals(paymentType)) {
            halfRateLabel.setFont(halfRateLabel.getFont().deriveFont(Font.BOLD, 16f));
            halfRateLabel.setForeground(new Color(0, 150, 0));
        } else if ("check".equals(paymentType)) {
            thirdRateLabel.setFont(thirdRateLabel.getFont().deriveFont(Font.BOLD, 16f));
            thirdRateLabel.setForeground(new Color(0, 150, 0));
        }
    }
}
