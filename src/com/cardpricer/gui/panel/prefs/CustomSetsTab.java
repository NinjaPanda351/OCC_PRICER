package com.cardpricer.gui.panel.prefs;

import com.cardpricer.util.AppTheme;
import com.cardpricer.util.SetList;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Sets tab: add or remove set codes without editing source code.
 *
 * <p>Built-in sets are always present and cannot be removed here.
 * Only user-added sets are shown and editable.
 */
public final class CustomSetsTab {

    private DefaultTableModel tableModel;
    private JLabel            statusLabel;
    private JPanel            panel;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Builds and returns the Custom Sets tab panel. */
    public JPanel build() {
        panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(15, 10, 10, 10));

        // Help text
        JLabel help = new JLabel(
                "<html>Add new set codes here so they appear in the Bulk Pricer and card lookup.<br>"
                + "Built-in sets are always available and are not shown below.<br>"
                + "<b>Custom Code</b>: what you type (e.g. <code>TMT</code>).&nbsp;&nbsp;"
                + "<b>Scryfall Code</b>: only needed if Scryfall uses a different code "
                + "(leave blank if the same).</html>");
        help.setFont(help.getFont().deriveFont(Font.ITALIC, 11f));
        help.setForeground(UIManager.getColor("Label.disabledForeground"));
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(help, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel(
                new String[]{"Custom Code", "Scryfall Code (if different)"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(26);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("User-Added Sets"));
        panel.add(scroll, BorderLayout.CENTER);

        // Buttons + status
        JButton addBtn    = new JButton("Add Row");
        JButton removeBtn = AppTheme.dangerButton("Remove Selected");
        JButton saveBtn   = AppTheme.primaryButton("Save");

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        addBtn.addActionListener(e -> {
            tableModel.addRow(new String[]{"", ""});
            int last = tableModel.getRowCount() - 1;
            table.setRowSelectionInterval(last, last);
            table.scrollRectToVisible(table.getCellRect(last, 0, true));
            table.editCellAt(last, 0);
        });

        removeBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                if (table.isEditing()) table.getCellEditor().stopCellEditing();
                tableModel.removeRow(row);
                statusLabel.setText("Row removed — click Save to persist.");
                statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            }
        });

        saveBtn.addActionListener(e -> {
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            saveEntries();
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.add(addBtn);
        btns.add(removeBtn);
        btns.add(saveBtn);
        btns.add(statusLabel);
        panel.add(btns, BorderLayout.SOUTH);

        loadEntries();
        return panel;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void loadEntries() {
        tableModel.setRowCount(0);
        for (String[] entry : SetList.getCustomSetEntries()) {
            tableModel.addRow(new String[]{entry[0], entry[1]});
        }
    }

    private void saveEntries() {
        List<String[]> entries = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String code = ((String) tableModel.getValueAt(i, 0)).trim().toUpperCase();
            String scry = ((String) tableModel.getValueAt(i, 1)).trim().toLowerCase();
            if (code.isEmpty()) continue;
            if (SetList.isBuiltinSet(code)) {
                JOptionPane.showMessageDialog(panel,
                        "\"" + code + "\" is already a built-in set — no need to add it here.",
                        "Duplicate", JOptionPane.WARNING_MESSAGE);
                return;
            }
            entries.add(new String[]{code, scry});
        }
        SetList.saveCustomSets(entries);
        loadEntries(); // re-render cleaned/sorted view
        statusLabel.setText("Saved — " + entries.size() + " custom set(s) active.");
        statusLabel.setForeground(new Color(0, 150, 0));
    }
}
