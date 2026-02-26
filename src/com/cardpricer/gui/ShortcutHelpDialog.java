package com.cardpricer.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * A non-modal shortcut cheat-sheet dialog triggered by F1 or the [?] button in TradePanel.
 * Only one instance is shown at a time per parent window.
 */
public class ShortcutHelpDialog extends JDialog {

    private static ShortcutHelpDialog instance;

    private static final Object[][] SHORTCUTS = {
        {"Enter",         "Add card from code field"},
        {"Ctrl+F / F2",   "Search by card name"},
        {"Numpad +",      "Duplicate selected card"},
        {"Ctrl+Z",        "Undo last added card"},
        {"F1",            "Show this help dialog"},
    };

    private ShortcutHelpDialog(Window owner) {
        super(owner, "Keyboard Shortcuts", ModalityType.MODELESS);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildContent();
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the help dialog (or brings the existing instance to front).
     *
     * @param owner  the parent window (may be null)
     */
    public static void show(Window owner) {
        if (instance != null && instance.isVisible()) {
            instance.toFront();
            instance.requestFocus();
            return;
        }
        instance = new ShortcutHelpDialog(owner);
        instance.setVisible(true);
    }

    // -------------------------------------------------------------------------

    private void buildContent() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Header
        JLabel header = new JLabel("Trade Panel — Keyboard Shortcuts");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 15f));
        content.add(header, BorderLayout.NORTH);

        // Table
        String[] cols = {"Shortcut", "Action"};
        DefaultTableModel model = new DefaultTableModel(SHORTCUTS, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setFont(new Font("Monospaced", Font.PLAIN, 13));
        table.setRowHeight(26);
        table.setFocusable(false);
        table.setShowGrid(true);
        table.setGridColor(UIManager.getColor("Separator.foreground"));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(280);

        // Bold the shortcut column
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            private final Font bold = new Font("Monospaced", Font.BOLD, 13);
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setFont(bold);
                return this;
            }
        });

        content.add(new JScrollPane(table), BorderLayout.CENTER);

        // Close button
        JButton closeBtn = new JButton("Close");
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(closeBtn);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
    }
}
