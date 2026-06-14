package com.cardpricer.util;

import javax.swing.*;
import javax.swing.text.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * POS-style money input field.
 *
 * <p>Digits shift in from the right (cents mode): typing "5000" displays "50.00".
 * Pressing "." switches to free-form decimal entry: the digits typed so far
 * become the dollar part and up to two more digits become the cents part
 * (e.g. typing "5" then "." then "0" "0" gives "5.00").
 * Backspace removes the last digit in either mode; backspacing past the decimal
 * point returns to cents mode.
 */
public class PosMoneyField extends JTextField {

    private final StringBuilder intPart = new StringBuilder();  // digit(s) before decimal
    private final StringBuilder decPart = new StringBuilder();  // 0–2 digits after decimal
    private boolean inDecimalMode = false;
    private boolean settingValue   = false;

    public PosMoneyField() {
        super(10);
        setHorizontalAlignment(JTextField.RIGHT);
        ((AbstractDocument) getDocument()).setDocumentFilter(new PosFilter());
    }

    /** Returns the current value as a BigDecimal, or ZERO if the field is empty. */
    public BigDecimal getValue() {
        String text = getText().trim();
        if (text.isEmpty()) return BigDecimal.ZERO;
        if (text.endsWith(".")) text += "0";
        try {
            return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Pre-fills the field with a given value in free-form decimal mode.
     * Call this to show an existing price for editing (e.g. from the table cell editor).
     */
    public void setValue(BigDecimal val) {
        settingValue = true;
        intPart.setLength(0);
        decPart.setLength(0);
        inDecimalMode = false;
        if (val != null && val.compareTo(BigDecimal.ZERO) != 0) {
            inDecimalMode = true;
            String formatted = String.format("%.2f", val);
            String[] parts = formatted.split("\\.", 2);
            intPart.append(parts[0]);
            if (parts.length > 1) decPart.append(parts[1]);
        }
        super.setText(inDecimalMode ? String.format("%.2f", val) : "");
        settingValue = false;
    }

    // -------------------------------------------------------------------------

    private class PosFilter extends DocumentFilter {

        @Override
        public void insertString(FilterBypass fb, int off, String str, AttributeSet a)
                throws BadLocationException {
            if (settingValue) { super.insertString(fb, off, str, a); return; }
            if (str != null) handleInput(fb, str);
        }

        @Override
        public void replace(FilterBypass fb, int off, int len, String str, AttributeSet a)
                throws BadLocationException {
            if (settingValue) { super.replace(fb, off, len, str, a); return; }
            // Replacing the entire content (e.g. selectAll → type) → reset state first.
            if (off == 0 && len == fb.getDocument().getLength()) {
                intPart.setLength(0);
                decPart.setLength(0);
                inDecimalMode = false;
            }
            if (str != null && !str.isEmpty()) handleInput(fb, str);
        }

        @Override
        public void remove(FilterBypass fb, int off, int len)
                throws BadLocationException {
            if (settingValue) { super.remove(fb, off, len); return; }
            // Clearing all text (e.g. from setText("")) → reset state.
            if (off == 0 && len == fb.getDocument().getLength()) {
                intPart.setLength(0);
                decPart.setLength(0);
                inDecimalMode = false;
                super.remove(fb, off, len);
                return;
            }
            handleBackspace(fb);
        }

        // ------------------------------------------------------------------

        private void handleInput(FilterBypass fb, String str) throws BadLocationException {
            for (char c : str.toCharArray()) {
                if (Character.isDigit(c)) {
                    if (!inDecimalMode) {
                        if (intPart.length() < 8) {   // guard against overflow
                            intPart.append(c);
                            refreshCents(fb);
                        }
                    } else {
                        if (decPart.length() < 2) {
                            decPart.append(c);
                            refreshDecimal(fb);
                        }
                    }
                } else if (c == '.' && !inDecimalMode) {
                    inDecimalMode = true;
                    decPart.setLength(0);
                    refreshDecimal(fb);
                }
                // all other characters (e.g. '$', letters) are silently ignored
            }
        }

        private void handleBackspace(FilterBypass fb) throws BadLocationException {
            if (inDecimalMode) {
                if (!decPart.isEmpty()) {
                    decPart.deleteCharAt(decPart.length() - 1);
                    refreshDecimal(fb);
                } else {
                    // backspace past the '.' → return to cents mode
                    inDecimalMode = false;
                    refreshCents(fb);
                }
            } else {
                if (!intPart.isEmpty()) {
                    intPart.deleteCharAt(intPart.length() - 1);
                    refreshCents(fb);
                }
            }
        }

        /** Interprets intPart digits as cents and updates the displayed text. */
        private void refreshCents(FilterBypass fb) throws BadLocationException {
            String display;
            if (intPart.isEmpty()) {
                display = "";
            } else {
                long cents = Long.parseLong(intPart.toString());
                display = String.format("%.2f", cents / 100.0);
            }
            super.replace(fb, 0, fb.getDocument().getLength(), display, null);
        }

        /** Updates the display using intPart as dollars and decPart as cents. */
        private void refreshDecimal(FilterBypass fb) throws BadLocationException {
            String dollars = intPart.isEmpty() ? "0" : intPart.toString();
            String display = dollars + "." + decPart;
            super.replace(fb, 0, fb.getDocument().getLength(), display, null);
        }
    }
}
