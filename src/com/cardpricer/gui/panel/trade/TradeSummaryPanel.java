package com.cardpricer.gui.panel.trade;

import com.cardpricer.util.AppTheme;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Displays the running trade total and effective payout rates.
 *
 * <p>Rates are computed dynamically from the accumulated per-row tiered payouts
 * supplied by {@link com.cardpricer.service.BuyRateService}, rather than using
 * flat 50% / 33% constants.  Call {@link #update} after every table change.
 */
public class TradeSummaryPanel extends JPanel {

    private final JLabel totalPriceLabel;
    private final JLabel creditPayoutLabel;
    private final JLabel checkPayoutLabel;

    /** Constructs the summary panel and initialises all value labels to zero. */
    public TradeSummaryPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(AppTheme.sectionBorder("Value Summary"));

        totalPriceLabel = new JLabel("TOTAL: $0.00 (0 cards)");
        totalPriceLabel.setFont(totalPriceLabel.getFont().deriveFont(Font.BOLD, 20f));
        totalPriceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        creditPayoutLabel = new JLabel("CREDIT PAYOUT (50%): $0.00");
        creditPayoutLabel.setFont(creditPayoutLabel.getFont().deriveFont(Font.PLAIN, 14f));
        creditPayoutLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        checkPayoutLabel = new JLabel("CHECK PAYOUT (33%): $0.00");
        checkPayoutLabel.setFont(checkPayoutLabel.getFont().deriveFont(Font.PLAIN, 14f));
        checkPayoutLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        add(totalPriceLabel);
        add(Box.createVerticalStrut(8));
        add(creditPayoutLabel);
        add(Box.createVerticalStrut(5));
        add(checkPayoutLabel);
    }

    /**
     * Refreshes all labels and highlights the rate that matches paymentType.
     *
     * @param total         total market value of all cards in the trade
     * @param totalQty      total card count across all rows
     * @param paymentType   "credit", "check", "inventory", or "partial"
     * @param creditPayout  accumulated credit payout from tiered rules
     * @param checkPayout   accumulated check payout from tiered rules
     */
    public void update(BigDecimal total, int totalQty, String paymentType,
                       BigDecimal creditPayout, BigDecimal checkPayout) {
        totalPriceLabel.setText(String.format("TOTAL: $%.2f (%d cards)", total, totalQty));

        // Compute effective percentages for display
        String creditPct = effectivePct(creditPayout, total);
        String checkPct  = effectivePct(checkPayout,  total);

        creditPayoutLabel.setText(String.format("CREDIT PAYOUT (%s%%): $%.2f", creditPct, creditPayout));
        checkPayoutLabel.setText(String.format("CHECK PAYOUT (%s%%): $%.2f",  checkPct,  checkPayout));

        highlightPaymentRate(paymentType);
        revalidate();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes {@code payout / total * 100} as a display string, or returns the
     * raw payout amount string when total is zero to avoid divide-by-zero.
     */
    private String effectivePct(BigDecimal payout, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return "—";
        }
        BigDecimal pct = payout.divide(total, 4, RoundingMode.HALF_UP)
                               .multiply(new BigDecimal("100"))
                               .setScale(1, RoundingMode.HALF_UP);
        // Strip trailing ".0" for clean display (e.g. "50" not "50.0")
        String s = pct.stripTrailingZeros().toPlainString();
        return s;
    }

    private void highlightPaymentRate(String paymentType) {
        // Reset both to normal
        creditPayoutLabel.setFont(creditPayoutLabel.getFont().deriveFont(Font.PLAIN, 14f));
        checkPayoutLabel.setFont(checkPayoutLabel.getFont().deriveFont(Font.PLAIN, 14f));
        creditPayoutLabel.setForeground(UIManager.getColor("Label.foreground"));
        checkPayoutLabel.setForeground(UIManager.getColor("Label.foreground"));

        if ("credit".equals(paymentType)) {
            creditPayoutLabel.setFont(creditPayoutLabel.getFont().deriveFont(Font.BOLD, 16f));
            creditPayoutLabel.setForeground(AppTheme.SUCCESS);
        } else if ("check".equals(paymentType)) {
            checkPayoutLabel.setFont(checkPayoutLabel.getFont().deriveFont(Font.BOLD, 16f));
            checkPayoutLabel.setForeground(AppTheme.SUCCESS);
        }
    }
}
