package com.cardpricer.gui.dialog;

import com.cardpricer.service.BugReportService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Prepares a report that the user reviews and sends from their email application.
 */
public final class HelpDialog extends JDialog {

    private final JTextField subjectField;
    private final JTextArea  bodyArea;
    private final JButton    sendBtn;
    private final JLabel     statusLabel;

    public static void show(Window parent) {
        new HelpDialog(parent).setVisible(true);
    }

    private HelpDialog(Window parent) {
        super(parent, "Report Bug", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(18, 18, 18, 18));

        // Header
        JLabel header = new JLabel("Prepare a Bug Report");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));

        JLabel sub = new JLabel("Review and send in your email app, or copy the report to " + BugReportService.RECIPIENT + ".");
        sub.setFont(sub.getFont().deriveFont(11f));
        sub.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.add(header);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(sub);

        // Form
        JPanel form = new JPanel(new BorderLayout(0, 10));

        JPanel subjectRow = new JPanel(new BorderLayout(8, 0));
        JLabel subjectLabel = new JLabel("Subject:");
        subjectLabel.setPreferredSize(new Dimension(60, 0));
        subjectField = new JTextField();
        subjectRow.add(subjectLabel, BorderLayout.WEST);
        subjectRow.add(subjectField, BorderLayout.CENTER);

        JPanel bodyPanel = new JPanel(new BorderLayout(8, 0));
        JLabel bodyLabel = new JLabel("Message:");
        bodyLabel.setPreferredSize(new Dimension(60, 0));
        bodyLabel.setVerticalAlignment(SwingConstants.TOP);
        bodyArea = new JTextArea(10, 40);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyPanel.add(bodyLabel, BorderLayout.WEST);
        bodyPanel.add(bodyScroll, BorderLayout.CENTER);

        form.add(subjectRow, BorderLayout.NORTH);
        form.add(bodyPanel, BorderLayout.CENTER);

        // Status label (shows sending progress / errors)
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

        // Buttons
        sendBtn = new JButton("Open Email Draft");
        sendBtn.putClientProperty("JButton.buttonType", "roundRect");
        sendBtn.addActionListener(e -> openEmailDraft());

        JButton copyBtn = new JButton("Copy Report");
        copyBtn.addActionListener(e -> {
            try {
                String report = "To: " + BugReportService.RECIPIENT + "\nSubject: "
                        + subjectField.getText() + "\n\n" + bodyArea.getText();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(report), null);
                statusLabel.setText("Copied. Paste into your email app.");
            } catch (IllegalStateException ex) {
                statusLabel.setText("Clipboard unavailable. Select and copy the message.");
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.putClientProperty("JButton.buttonType", "roundRect");
        cancelBtn.addActionListener(e -> dispose());

        JPanel btnPanel = new JPanel(new BorderLayout());
        btnPanel.add(statusLabel, BorderLayout.WEST);
        JPanel btnRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRight.add(cancelBtn);
        btnRight.add(copyBtn);
        btnRight.add(sendBtn);
        btnPanel.add(btnRight, BorderLayout.EAST);

        content.add(headerPanel, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        content.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(parent);

        SwingUtilities.invokeLater(subjectField::requestFocusInWindow);
    }

    private void openEmailDraft() {
        String subject = subjectField.getText().trim();
        String body    = bodyArea.getText().trim();

        if (subject.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a subject line.",
                    "Subject Required", JOptionPane.WARNING_MESSAGE);
            subjectField.requestFocusInWindow();
            return;
        }
        if (body.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a message.",
                    "Message Required", JOptionPane.WARNING_MESSAGE);
            bodyArea.requestFocusInWindow();
            return;
        }

        // Keep the report available while the email application opens.
        sendBtn.setEnabled(false);
        subjectField.setEnabled(false);
        bodyArea.setEnabled(false);
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        statusLabel.setText("Opening email app…");

        // Opening an external application must not block the EDT.
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                BugReportService.openDraft(subject, body);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // rethrow any exception
                    JOptionPane.showMessageDialog(HelpDialog.this,
                            "Draft opened. Review and send it in your email app.\n"
                                    + "If the draft did not appear, use Copy Report.",
                            "Email Draft", JOptionPane.INFORMATION_MESSAGE);
                    statusLabel.setText("Review and send in your email app.");
                } catch (Exception ex) {
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("Could not open email. Use Copy Report.");
                } finally {
                    sendBtn.setEnabled(true);
                    subjectField.setEnabled(true);
                    bodyArea.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
