package com.cardpricer.gui.dialog;

import com.cardpricer.service.HelpEmailService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Modal dialog that lets users compose a bug report or feature request
 * and send it directly to Jayden without opening an external email client.
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
        super(parent, "Help! Jayden", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(18, 18, 18, 18));

        // Header
        JLabel header = new JLabel("Send a message to Jayden");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));

        JLabel sub = new JLabel("Report a bug or request a feature — sent directly to Jayden's inbox.");
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
        sendBtn = new JButton("Send");
        sendBtn.putClientProperty("JButton.buttonType", "roundRect");
        sendBtn.addActionListener(e -> sendEmail());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.putClientProperty("JButton.buttonType", "roundRect");
        cancelBtn.addActionListener(e -> dispose());

        JPanel btnPanel = new JPanel(new BorderLayout());
        btnPanel.add(statusLabel, BorderLayout.WEST);
        JPanel btnRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRight.add(cancelBtn);
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

    private void sendEmail() {
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

        // Disable UI while sending
        sendBtn.setEnabled(false);
        subjectField.setEnabled(false);
        bodyArea.setEnabled(false);
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        statusLabel.setText("Sending…");

        // Send on a background thread so the EDT stays responsive
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                HelpEmailService.send(subject, body);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // rethrow any exception
                    JOptionPane.showMessageDialog(HelpDialog.this,
                            "Message sent! Jayden will get back to you soon.",
                            "Sent", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("Error: " + cause.getMessage());
                    sendBtn.setEnabled(true);
                    subjectField.setEnabled(true);
                    bodyArea.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
