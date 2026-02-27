package com.cardpricer.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * A non-modal, panel-agnostic help dialog triggered by any panel's [?] button.
 * One instance per dialog title is kept alive; re-opening brings it to front.
 *
 * <p>Rows whose first column begins with {@code "---"} are rendered as
 * section-header dividers (gray background, italic label) spanning both columns.
 */
public class ShortcutHelpDialog extends JDialog {

    /** One instance per dialog title so multiple panels can each have their own. */
    private static final Map<String, ShortcutHelpDialog> INSTANCES = new HashMap<>();

    private ShortcutHelpDialog(Window owner, String dialogTitle,
                               String[] columnNames, String[][] rows) {
        super(owner, dialogTitle, ModalityType.MODELESS);
        setResizable(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildContent(dialogTitle, columnNames, rows);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the help dialog for the given panel (or brings an existing one to front).
     *
     * <p>Rows whose first column starts with {@code "---"} are treated as
     * section headers and rendered as full-width dividers. Their second column
     * value is ignored.
     *
     * @param owner       parent window (may be null)
     * @param dialogTitle title shown in the window chrome and used as the singleton key
     * @param columnNames two-element array of column header names
     * @param rows        table data — each element is a two-element string array
     */
    public static void show(Window owner, String dialogTitle,
                            String[] columnNames, String[][] rows) {
        ShortcutHelpDialog existing = INSTANCES.get(dialogTitle);
        if (existing != null && existing.isVisible()) {
            existing.toFront();
            existing.requestFocus();
            return;
        }
        ShortcutHelpDialog dialog = new ShortcutHelpDialog(owner, dialogTitle, columnNames, rows);
        INSTANCES.put(dialogTitle, dialog);
        dialog.setVisible(true);
    }

    // -------------------------------------------------------------------------

    private void buildContent(String dialogTitle, String[] columnNames, String[][] rows) {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Header
        JLabel header = new JLabel(dialogTitle);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 15f));
        content.add(header, BorderLayout.NORTH);

        // Table — non-editable, data comes straight from the caller
        DefaultTableModel model = new DefaultTableModel(rows, columnNames) {
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

        // Combined renderer: bold key column + section-header dividers
        Font boldMono    = new Font("Monospaced", Font.BOLD,  13);
        Font plainMono   = new Font("Monospaced", Font.PLAIN, 13);
        Font dividerFont = UIManager.getFont("Label.font")
                               .deriveFont(Font.BOLD | Font.ITALIC, 11f);
        Color dividerBg  = UIManager.getColor("Table.gridColor");
        Color dividerFg  = UIManager.getColor("Label.disabledForeground");

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                String key = (String) t.getModel().getValueAt(r, 0);
                if (key != null && key.startsWith("---")) {
                    String label = key.replaceAll("^-+\\s*", "");
                    setText(c == 0 ? label : "");
                    setFont(dividerFont);
                    setForeground(dividerFg);
                    setBackground(dividerBg != null ? dividerBg : new Color(100, 100, 100, 60));
                } else {
                    setFont(c == 0 ? boldMono : plainMono);
                    setBackground(sel ? t.getSelectionBackground() : t.getBackground());
                    setForeground(sel ? t.getSelectionForeground() : t.getForeground());
                }
                return this;
            }
        });

        // Auto-fit each column to the widest cell (header + all data rows)
        int totalColWidth = 0;
        for (int col = 0; col < table.getColumnCount(); col++) {
            // Measure the column header
            Component hComp = table.getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(table,
                            table.getColumnModel().getColumn(col).getHeaderValue(),
                            false, false, -1, col);
            int maxWidth = hComp.getPreferredSize().width + 16;
            // Measure every data cell using the actual renderer
            for (int row = 0; row < table.getRowCount(); row++) {
                Component cComp = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
                maxWidth = Math.max(maxWidth, cComp.getPreferredSize().width + 16);
            }
            table.getColumnModel().getColumn(col).setPreferredWidth(maxWidth);
            totalColWidth += maxWidth;
        }

        // Height: cap at 16 visible rows; width: sum of measured column widths
        int visibleRows = Math.min(rows.length, 16);
        int tableHeight = visibleRows * table.getRowHeight()
                        + table.getTableHeader().getPreferredSize().height;
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(totalColWidth + 4, tableHeight));
        content.add(scroll, BorderLayout.CENTER);

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
