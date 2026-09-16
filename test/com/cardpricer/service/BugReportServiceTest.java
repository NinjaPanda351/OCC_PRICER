package com.cardpricer.service;

import org.junit.jupiter.api.Test;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class BugReportServiceTest {
    @Test
    void draftPreservesTextAndCannotInjectRecipientsOrHeaders() {
        String subject = "Price & finish? + 日本語";
        String body = "Line 1\r\nLine 2 &bcc=someone@example.com #fragment + 100%";
        var uri = BugReportService.draftUri(subject, body);
        assertEquals("mailto", uri.getScheme());
        assertNull(uri.getFragment());
        String raw = uri.getRawSchemeSpecificPart();
        assertTrue(raw.startsWith(BugReportService.RECIPIENT + "?subject="));
        String[] fields = raw.substring(raw.indexOf('?') + 1).split("&");
        assertEquals(2, fields.length);
        assertEquals(subject, URLDecoder.decode(fields[0].substring("subject=".length()), StandardCharsets.UTF_8));
        assertEquals(body, URLDecoder.decode(fields[1].substring("body=".length()), StandardCharsets.UTF_8));
        assertFalse(raw.contains("+"));
    }

    @Test
    void rejectsBlankReportsAndMultilineSubjects() {
        assertThrows(IllegalArgumentException.class, () -> BugReportService.draftUri("", "body"));
        assertThrows(IllegalArgumentException.class, () -> BugReportService.draftUri("subject", " "));
        assertThrows(IllegalArgumentException.class, () -> BugReportService.draftUri("subject\r\nBcc: x", "body"));
    }
}
