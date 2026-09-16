package com.cardpricer.gui.panel.trade;

import com.cardpricer.util.AppTheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Self-contained payment-method selector (store credit, check, inventory, partial).
 * Owns the grouped payout buttons, partial-split fields, and split-recalculation logic.
 *
 * <p>Pass a {@code Runnable} to the constructor; it is called whenever the
 * selection changes so the parent can react (e.g. enable/disable check number
 * field and refresh the trade summary).
 */
public class PaymentTypePanel extends JPanel {

    private final JToggleButton storeCreditRadio;
    private final JToggleButton checkRadio;
    private final JToggleButton inventoryRadio;
    private final JToggleButton partialRadio;

    private final JTextField partialCreditField;
    private final JTextField partialCheckField;
    private final JPanel partialPaymentPanel;
    private JLabel partialTotalLabel;

    /** Current trade total (market value), updated via setTotal(). */
    private BigDecimal currentTotal      = BigDecimal.ZERO;
    /** Tiered credit payout total (includes bounty rates), updated via setTotal(). */
    private BigDecimal currentCreditSeed = BigDecimal.ZERO;
    /** Tiered check payout total (includes bounty rates), updated via setTotal(). */
    private BigDecimal currentCheckSeed  = BigDecimal.ZERO;

    private final Runnable onSelectionChanged;

    /**
     * Creates the payment-type selector panel.
     *
     * @param onSelectionChanged callback invoked on the EDT whenever the user
     *                           changes the selected payment method
     */
    public PaymentTypePanel(Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // --- Radio buttons row ---
        JPanel radioPanel = new JPanel(new com.cardpricer.gui.WrapLayout(FlowLayout.LEFT, 6, 0));

        setOpaque(false); radioPanel.setOpaque(false);
        ButtonGroup paymentGroup = new ButtonGroup();
        storeCreditRadio = new JToggleButton("Store credit");
        checkRadio = new JToggleButton("Check");
        inventoryRadio = new JToggleButton("Inventory");
        partialRadio = new JToggleButton("Split payment");

        for (JToggleButton option : new JToggleButton[]{storeCreditRadio, checkRadio, inventoryRadio, partialRadio}) {
            option.putClientProperty("JButton.buttonType", "roundRect");
            option.setMargin(new Insets(6, 12, 6, 12));
        }
        inventoryRadio.setToolTipText("Inventory transfer without a payout");
        storeCreditRadio.setSelected(true); // Default

        paymentGroup.add(storeCreditRadio);
        paymentGroup.add(checkRadio);
        paymentGroup.add(inventoryRadio);
        paymentGroup.add(partialRadio);

        // Build partial payment panel before wiring listeners (it's referenced there)
        partialCreditField = new JTextField(8);
        partialCheckField = new JTextField(8);
        partialPaymentPanel = buildPartialPaymentPanel();
        partialPaymentPanel.setVisible(false);

        // --- Radio listeners ---
        storeCreditRadio.addActionListener(e -> {
            partialPaymentPanel.setVisible(false);
            onSelectionChanged.run();
        });

        checkRadio.addActionListener(e -> {
            partialPaymentPanel.setVisible(false);
            onSelectionChanged.run();
        });

        inventoryRadio.addActionListener(e -> {
            partialPaymentPanel.setVisible(false);
            onSelectionChanged.run();
        });

        partialRadio.addActionListener(e -> {
            partialPaymentPanel.setVisible(true);
            updatePartialSplit();
            onSelectionChanged.run();
        });

        radioPanel.add(storeCreditRadio);
        radioPanel.add(checkRadio);
        radioPanel.add(inventoryRadio);
        radioPanel.add(partialRadio);

        add(radioPanel);
        add(partialPaymentPanel);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void onPaymentEdited(Runnable changed) {
        javax.swing.event.DocumentListener listener=new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { changed.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { changed.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { changed.run(); }
        };
        partialCreditField.getDocument().addDocumentListener(listener); partialCheckField.getDocument().addDocumentListener(listener);
        for (JToggleButton button:new JToggleButton[]{storeCreditRadio,checkRadio,inventoryRadio,partialRadio}) button.addActionListener(e -> changed.run());
    }
    public void restore(String type, BigDecimal credit, BigDecimal check) {
        switch (type) {
            case "credit" -> storeCreditRadio.setSelected(true);
            case "check" -> checkRadio.setSelected(true);
            case "inventory" -> inventoryRadio.setSelected(true);
            case "partial" -> partialRadio.setSelected(true);
            default -> throw new IllegalArgumentException("Unsupported payment: " + type);
        }
        partialCreditField.setText(credit.toPlainString()); partialCheckField.setText(check.toPlainString());
        partialPaymentPanel.setVisible("partial".equals(type));
    }

    /** Returns "credit", "check", "inventory", or "partial". */
    public String getPaymentType() {
        if (checkRadio.isSelected())     return "check";
        if (inventoryRadio.isSelected()) return "inventory";
        if (partialRadio.isSelected())   return "partial";
        return "credit";
    }

    /** Returns {@code true} when the Check radio is selected. */
    public boolean isCheckSelected() {
        return checkRadio.isSelected();
    }

    /**
     * Supplies the current trade total so split fields can auto-populate when
     * the user selects the Partial option.
     * Delegates to {@link #setTotal(BigDecimal, BigDecimal, BigDecimal)} with
     * {@code null} seeds (falls back to equal-split behaviour).
     */
    public void setTotal(BigDecimal total) {
        setTotal(total, null, null);
    }

    /**
     * Supplies the current trade total along with tiered payout seeds.
     *
     * <p>When the Partial radio is selected and the split panel is visible, the
     * credit and check fields are pre-populated with {@code creditSeed} /
     * {@code checkSeed} respectively (the tiered payout amounts computed by
     * {@link com.cardpricer.service.BuyRateService}).  If either seed is
     * {@code null} the method falls back to an equal-split of the total.
     *
     * @param total      current total market value
     * @param creditSeed tiered credit payout suggestion, or {@code null}
     * @param checkSeed  tiered check payout suggestion, or {@code null}
     */
    public void setTotal(BigDecimal total, BigDecimal creditSeed, BigDecimal checkSeed) {
        this.currentTotal      = total;
        this.currentCreditSeed = creditSeed != null ? creditSeed : BigDecimal.ZERO;
        this.currentCheckSeed  = checkSeed  != null ? checkSeed  : BigDecimal.ZERO;
        if (partialRadio.isSelected()) {
            updatePartialTotal();
        }
    }

    /**
     * Returns the credit payout amount entered in the partial-payment panel.
     *
     * @throws NumberFormatException if the field contains non-numeric text
     */
    public BigDecimal getPartialCreditPayout() {
        String text = partialCreditField.getText().trim();
        return text.isEmpty() ? BigDecimal.ZERO : new BigDecimal(text);
    }

    /**
     * Returns the check payout amount entered in the partial-payment panel.
     *
     * @throws NumberFormatException if the field contains non-numeric text
     */
    public BigDecimal getPartialCheckPayout() {
        String text = partialCheckField.getText().trim();
        return text.isEmpty() ? BigDecimal.ZERO : new BigDecimal(text);
    }

    // -------------------------------------------------------------------------
    // Partial-payment panel construction and logic
    // -------------------------------------------------------------------------

    private JPanel buildPartialPaymentPanel() {
        JPanel panel = AppTheme.transparent(new com.cardpricer.gui.WrapLayout(FlowLayout.LEFT, 10, 4));
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));

        panel.add(new JLabel("Store Credit $"));

        partialCreditField.setHorizontalAlignment(JTextField.RIGHT);
        partialCreditField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() != KeyEvent.VK_TAB) {
                    updatePartialCheck();
                }
            }
        });
        panel.add(partialCreditField);

        panel.add(new JLabel("  +  Check $"));

        partialCheckField.setHorizontalAlignment(JTextField.RIGHT);
        partialCheckField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() != KeyEvent.VK_TAB) {
                    updatePartialCredit();
                }
            }
        });
        panel.add(partialCheckField);

        partialTotalLabel = new JLabel("  =  $0.00");
        partialTotalLabel.setFont(partialTotalLabel.getFont().deriveFont(Font.BOLD));
        panel.add(partialTotalLabel);

        return panel;
    }

    private void updatePartialSplit() {
        if (partialCreditField.getText().isBlank() && partialCheckField.getText().isBlank()) {
            partialCreditField.setText(currentCreditSeed.toPlainString());
            partialCheckField.setText("0.00");
        }
        updatePartialTotal();
    }

    private void updatePartialCheck() {
        // User typed credit — back-calculate check using effective rates from seeds
        try {
            String creditText = partialCreditField.getText().trim();
            if (creditText.isEmpty()) {
                partialCheckField.setText("");
                updatePartialTotal();
                return;
            }
            if (currentTotal.compareTo(BigDecimal.ZERO) == 0) return;

            if (currentCreditSeed.signum() == 0) return;
            BigDecimal creditPayout = new BigDecimal(creditText);

            BigDecimal checkPayout = com.cardpricer.service.SettlementEngine.remainingCheck(currentCreditSeed, currentCheckSeed, creditPayout);
            partialCheckField.setText(String.format(java.util.Locale.ROOT, "%.2f", checkPayout));
            updatePartialTotal();
        } catch (Exception e) {
            updatePartialTotal();
        }
    }

    private void updatePartialCredit() {
        // User typed check — back-calculate credit using effective rates from seeds
        try {
            String checkText = partialCheckField.getText().trim();
            if (checkText.isEmpty()) {
                partialCreditField.setText("");
                updatePartialTotal();
                return;
            }
            if (currentTotal.compareTo(BigDecimal.ZERO) == 0) return;

            if (currentCheckSeed.signum() == 0) return;
            BigDecimal checkPayout = new BigDecimal(checkText);
            BigDecimal creditPayout = currentCreditSeed.multiply(currentCheckSeed.subtract(checkPayout))
                    .divide(currentCheckSeed, 2, RoundingMode.HALF_UP);
            partialCreditField.setText(String.format(java.util.Locale.ROOT, "%.2f", creditPayout));
            updatePartialTotal();
        } catch (Exception e) {
            updatePartialTotal();
        }
    }

    private void updatePartialTotal() {
        try {
            String creditText = partialCreditField.getText().trim();
            String checkText  = partialCheckField.getText().trim();

            BigDecimal creditPayout = creditText.isEmpty() ? BigDecimal.ZERO : new BigDecimal(creditText);
            BigDecimal checkPayout  = checkText.isEmpty()  ? BigDecimal.ZERO : new BigDecimal(checkText);
            new com.cardpricer.service.SettlementEngine().settle(java.util.List.of(
                    new com.cardpricer.service.SettlementEngine.Line("summary", 1, currentTotal,
                            currentCreditSeed, currentCheckSeed, true)), "partial", creditPayout, checkPayout);

            partialTotalLabel.setText(String.format(java.util.Locale.ROOT, "  =  $%.2f total", creditPayout.add(checkPayout)));
            partialTotalLabel.setForeground(AppTheme.success());
            partialTotalLabel.setToolTipText(null);
        } catch (Exception e) {
            partialTotalLabel.setText("  Review split");
            partialTotalLabel.setForeground(AppTheme.color("OCC.danger", AppTheme.DANGER));
            partialTotalLabel.setToolTipText(e.getMessage());
        }
    }
}
