package com.cardpricer.gui.panel.trade;

import com.cardpricer.util.AppTheme;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Three stable metrics keep the offer readable without changing settlement calculations. */
public class TradeSummaryPanel extends JPanel {
    private final JLabel totalPriceLabel = new JLabel("$0.00");
    private final JLabel creditPayoutLabel = new JLabel("$0.00");
    private final JLabel checkPayoutLabel = new JLabel("$0.00");
    private final JLabel cardCount = AppTheme.mutedLabel("0 cards in this trade");
    private final JLabel creditRate = AppTheme.mutedLabel("Quoted credit offer");
    private final JLabel checkRate = AppTheme.mutedLabel("Quoted check offer");
    private String selectedPayment = "credit";
    private final java.util.List<Metric> metrics = new java.util.ArrayList<>();
    private boolean compact;
    private record Metric(JPanel panel, JLabel title, JLabel amount, JLabel detail) {}

    public TradeSummaryPanel() {
        super(new GridLayout(1, 3, 12, 0)); setOpaque(false);
        add(metric("Market value", totalPriceLabel, cardCount));
        add(metric("Store credit", creditPayoutLabel, creditRate));
        add(metric("Check payout", checkPayoutLabel, checkRate));
    }
    private JPanel metric(String title, JLabel amount, JLabel detail) {
        JPanel metric = AppTheme.surface(new BorderLayout(0, 2), 8);
        JLabel titleLabel = AppTheme.mutedLabel(title);
        metric.add(titleLabel, BorderLayout.NORTH);
        amount.setFont(AppTheme.FONT_TITLE.deriveFont(20f));
        metric.add(amount, BorderLayout.CENTER); metric.add(detail, BorderLayout.SOUTH);
        metrics.add(new Metric(metric, titleLabel, amount, detail));
        return metric;
    }
    public void setCompact(boolean compact) {
        if (this.compact == compact) return;
        this.compact = compact;
        for (Metric metric : metrics) {
            metric.panel.removeAll(); metric.panel.setBorder(AppTheme.cardBorder(compact ? 6 : 8));
            metric.panel.add(metric.title, compact ? BorderLayout.WEST : BorderLayout.NORTH);
            metric.panel.add(metric.amount, compact ? BorderLayout.EAST : BorderLayout.CENTER);
            if (!compact) metric.panel.add(metric.detail, BorderLayout.SOUTH);
        }
        revalidate(); repaint();
    }
    public void update(BigDecimal total, int totalQty, String paymentType, BigDecimal creditPayout, BigDecimal checkPayout) {
        totalPriceLabel.setText(money(total)); creditPayoutLabel.setText(money(creditPayout)); checkPayoutLabel.setText(money(checkPayout));
        cardCount.setText(totalQty + (totalQty == 1 ? " card in this trade" : " cards in this trade"));
        creditRate.setText(effectivePct(creditPayout, total) + " of market" + ("credit".equals(paymentType) ? "  ·  Selected" : ""));
        checkRate.setText(effectivePct(checkPayout, total) + " of market" + ("check".equals(paymentType) ? "  ·  Selected" : ""));
        for (Metric metric : metrics) metric.panel.setToolTipText(metric.title.getText() + ": " + metric.amount.getText() + " — " + metric.detail.getText());
        selectedPayment = paymentType; updateColors(); revalidate();
    }
    @Override public void updateUI() { super.updateUI(); if (creditPayoutLabel != null) updateColors(); }
    private void updateColors() {
        Color normal = UIManager.getColor("Label.foreground");
        creditPayoutLabel.setForeground("credit".equals(selectedPayment) ? AppTheme.accent() : normal);
        checkPayoutLabel.setForeground("check".equals(selectedPayment) ? AppTheme.accent() : normal);
    }
    private static String money(BigDecimal value) { return "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String effectivePct(BigDecimal payout, BigDecimal total) {
        if (total.signum() == 0) return "—";
        return payout.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }
}
