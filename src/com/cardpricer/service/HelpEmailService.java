package com.cardpricer.service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Sends help / bug-report emails directly to Jayden via Gmail SMTP.
 * Uses a dedicated Gmail App Password — no external email client required.
 */
public final class HelpEmailService {

    private static final String SMTP_HOST  = "smtp.gmail.com";
    private static final int    SMTP_PORT  = 587;
    private static final String FROM_ADDR  = "occtradepricer@gmail.com";
    private static final String TO_ADDR    = "occtradepricer@gmail.com";
    private static final String APP_PASS   = "etsoggopbiqscrpq";

    private HelpEmailService() {}

    /**
     * Sends an email synchronously. Call this from a background thread
     * (e.g. SwingWorker) — never from the EDT.
     *
     * @param subject email subject line
     * @param body    plain-text message body
     * @throws MessagingException if the send fails for any reason
     */
    public static void send(String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth",            "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host",            SMTP_HOST);
        props.put("mail.smtp.port",            String.valueOf(SMTP_PORT));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout",           "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(FROM_ADDR, APP_PASS);
            }
        });

        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(FROM_ADDR));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO_ADDR));
        msg.setSubject(subject);
        msg.setText(body);

        Transport.send(msg);
    }
}
