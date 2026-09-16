package com.cardpricer.service;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Opens a report draft in the user's email application. Never sends mail itself. */
public final class BugReportService {
    public static final String RECIPIENT = "occtradepricer@gmail.com";

    private BugReportService() {}

    public static URI draftUri(String subject, String body) {
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("A subject and message are required.");
        }
        if (subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("The subject must be a single line.");
        }
        return URI.create("mailto:" + RECIPIENT + "?subject=" + encode(subject) + "&body=" + encode(body));
    }

    public static void openDraft(String subject, String body) throws IOException {
        URI uri = draftUri(subject, body);
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            throw new IOException("No supported email application is configured.");
        }
        Desktop.getDesktop().mail(uri);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
