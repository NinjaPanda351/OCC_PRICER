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

    /** Current trade total, updated via setTotal(). */
    private BigDecimal currentTotal = BigDecimal.ZERO;

    private final Runnable onSelectionChanged;

    public PaymentTypePanel(Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // --- Radio buttons row ---
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));

        ButtonGroup paymentGroup = new ButtonGroup();
        storeCreditRadio = new JRadioButton("Store Credit (50%)");
        checkRadio = new JRadioButton("Check (33.33%)");
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
     */
    public void setTotal(BigDecimal total) {
        this.currentTotal = total;
        if (partialRadio.isSelected() && partialPaymentPanel.isVisible()) {
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

        JLabel equalsLabel = new JLabel("  =  $0.00");
        equalsLabel.setFont(equalsLabel.getFont().deriveFont(Font.BOLD));
        panel.add(equalsLabel);

        return panel;
    }

    private void updatePartialSplit() {
        // Auto-calculate 50/50 split of current total
        try {
            BigDecimal half = currentTotal.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            partialCreditField.setText(String.format("%.2f", half));
            partialCheckField.setText(String.format("%.2f", half));
            updatePartialTotal();
        } catch (Exception e) {
            partialCreditField.setText("0.00");
            partialCheckField.setText("0.00");
        }
    }

    private void updatePartialCheck() {
        // User typed in credit payout — calculate corresponding check payout
        try {
            String creditText = partialCreditField.getText().trim();
            if (creditText.isEmpty()) {
                partialCheckField.setText("");
                updatePartialTotal();
                return;
            }

            BigDecimal creditPayout = new BigDecimal(creditText.replace(",", ""));
            // Credit is 50% of card value → value = credit / 0.50
            BigDecimal valueUsedForCredit = creditPayout.divide(new BigDecimal("0.50"), 2, RoundingMode.HALF_UP);
            BigDecimal remainingValue = currentTotal.subtract(valueUsedForCredit);
            if (remainingValue.compareTo(BigDecimal.ZERO) < 0) remainingValue = BigDecimal.ZERO;

            // Check payout is 1/3 of remaining value
            BigDecimal checkPayout = remainingValue.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
            partialCheckField.setText(String.format("%.2f", checkPayout));
            updatePartialTotal();
        } catch (Exception e) {
            // Invalid number, don't update
        }
    }

    private void updatePartialCredit() {
        // User typed in check payout — calculate corresponding credit payout
        try {
            String checkText = partialCheckField.getText().trim();
            if (checkText.isEmpty()) {
                partialCreditField.setText("");
                updatePartialTotal();
                return;
            }

            BigDecimal checkPayout = new BigDecimal(checkText.replace(",", ""));
            // Check is 1/3 of card value → value = check * 3
            BigDecimal valueUsedForCheck = checkPayout.multiply(new BigDecimal("3"));
            BigDecimal remainingValue = currentTotal.subtract(valueUsedForCheck);
            if (remainingValue.compareTo(BigDecimal.ZERO) < 0) remainingValue = BigDecimal.ZERO;

            // Credit payout is 50% of remaining value
            BigDecimal creditPayout = remainingValue.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
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

            // Value consumed: credit uses value/0.5, check uses value*3
            BigDecimal valueForCredit = creditPayout.divide(new BigDecimal("0.50"), 2, RoundingMode.HALF_UP);
            BigDecimal valueForCheck  = checkPayout.multiply(new BigDecimal("3"));
            BigDecimal totalValueUsed = valueForCredit.add(valueForCheck);

            // Find the equals label and update it
            for (Component comp : partialPaymentPanel.getComponents()) {
                if (comp instanceof JLabel) {
                    JLabel label = (JLabel) comp;
                    if (label.getText().startsWith("  =  ")) {
                        label.setText(String.format("  =  $%.2f value used", totalValueUsed));

                        BigDecimal diff = totalValueUsed.subtract(currentTotal).abs();
                        label.setForeground(diff.compareTo(new BigDecimal("0.10")) <= 0
                                ? new Color(0, 150, 0)
                                : Color.RED);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
}
