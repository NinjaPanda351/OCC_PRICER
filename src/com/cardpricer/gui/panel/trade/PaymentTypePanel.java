package com.cardpricer.gui.panel.trade;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Self-contained payment-method selector (store credit, check, inventory, partial).
 * Owns all radio buttons, partial-split fields, and split-recalculation logic.
 *
 * <p>Pass a {@code Runnable} to the constructor; it is called whenever the
 * selection changes so the parent can react (e.g. enable/disable check number
 * field and refresh the trade summary).
 */
public class PaymentTypePanel extends JPanel {

    private final JRadioButton storeCreditRadio;
    private final JRadioButton checkRadio;
    private final JRadioButton inventoryRadio;
    private final JRadioButton partialRadio;

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
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));

        ButtonGroup paymentGroup = new ButtonGroup();
        storeCreditRadio = new JRadioButton("Store Credit");
        checkRadio = new JRadioButton("Check");
        inventoryRadio = new JRadioButton("Inventory (No Payout)");
        partialRadio = new JRadioButton("Partial (Split Payment)");

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
            updatePartialSplit();
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
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Split Payment"),
                new EmptyBorder(5, 10, 5, 10)
        ));

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
        partialCreditField.setText("0.00");
        partialCheckField.setText("0.00");
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

            BigDecimal creditRate = currentCreditSeed.divide(currentTotal, 10, RoundingMode.HALF_UP);
            BigDecimal checkRate  = currentCheckSeed.divide(currentTotal, 10, RoundingMode.HALF_UP);
            if (creditRate.compareTo(BigDecimal.ZERO) == 0) return;

            BigDecimal creditPayout       = new BigDecimal(creditText.replace(",", ""));
            BigDecimal valueUsedForCredit = creditPayout.divide(creditRate, 2, RoundingMode.HALF_UP);
            BigDecimal remainingValue     = currentTotal.subtract(valueUsedForCredit);
            if (remainingValue.compareTo(BigDecimal.ZERO) < 0) remainingValue = BigDecimal.ZERO;

            BigDecimal checkPayout = remainingValue.multiply(checkRate).setScale(2, RoundingMode.HALF_UP);
            partialCheckField.setText(String.format("%.2f", checkPayout));
            updatePartialTotal();
        } catch (Exception e) {
            // Invalid number, don't update
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

            BigDecimal creditRate = currentCreditSeed.divide(currentTotal, 10, RoundingMode.HALF_UP);
            BigDecimal checkRate  = currentCheckSeed.divide(currentTotal, 10, RoundingMode.HALF_UP);
            if (checkRate.compareTo(BigDecimal.ZERO) == 0) return;

            BigDecimal checkPayout       = new BigDecimal(checkText.replace(",", ""));
            BigDecimal valueUsedForCheck = checkPayout.divide(checkRate, 2, RoundingMode.HALF_UP);
            BigDecimal remainingValue    = currentTotal.subtract(valueUsedForCheck);
            if (remainingValue.compareTo(BigDecimal.ZERO) < 0) remainingValue = BigDecimal.ZERO;

            BigDecimal creditPayout = remainingValue.multiply(creditRate).setScale(2, RoundingMode.HALF_UP);
            partialCreditField.setText(String.format("%.2f", creditPayout));
            updatePartialTotal();
        } catch (Exception e) {
            // Invalid number, don't update
        }
    }

    private void updatePartialTotal() {
        try {
            String creditText = partialCreditField.getText().trim();
            String checkText  = partialCheckField.getText().trim();

            BigDecimal creditPayout = creditText.isEmpty() ? BigDecimal.ZERO : new BigDecimal(creditText.replace(",", ""));
            BigDecimal checkPayout  = checkText.isEmpty()  ? BigDecimal.ZERO : new BigDecimal(checkText.replace(",", ""));

            partialTotalLabel.setText(String.format("  =  $%.2f total", creditPayout.add(checkPayout)));
            partialTotalLabel.setForeground(new Color(0, 150, 0));
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
}
