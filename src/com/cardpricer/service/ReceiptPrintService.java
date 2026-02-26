package com.cardpricer.service;

import javax.swing.*;
import java.awt.*;
import java.awt.print.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Prints trade receipts to a physical printer or saves them as PDF.
 * PDF generation uses a minimal hand-built PDF writer (no external dependencies).
 */
public class ReceiptPrintService {

    private static final int CHARS_PER_LINE = 100; // wider column → less wrapping
    private static final float FONT_SIZE = 6f;     // 6 pt → ~119 lines/page on Letter

    // -------------------------------------------------------------------------
    // Print
    // -------------------------------------------------------------------------

    /**
     * Opens the OS print dialog and prints {@code receiptContent} in monospace font.
     */
    public static void printReceipt(Component parent, String receiptContent) {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Trade Receipt");

        List<String> lines = wrapLines(receiptContent);

        PageFormat format = job.defaultPage();
        Paper paper = new Paper();
        double margin = 9; // 0.125 inch
        paper.setImageableArea(margin, margin,
                paper.getWidth() - 2 * margin,
                paper.getHeight() - 2 * margin);
        format.setPaper(paper);

        job.setPrintable((g, pf, pageIndex) -> {
            Graphics2D g2 = (Graphics2D) g;
            g2.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, (int) FONT_SIZE));
            FontMetrics fm = g2.getFontMetrics();
            int lineH = fm.getHeight();
            int lpp = Math.max(1, (int) (pf.getImageableHeight() / lineH));

            int totalPages = (lines.size() + lpp - 1) / lpp;
            if (pageIndex >= totalPages) return Printable.NO_SUCH_PAGE;

            g2.translate(pf.getImageableX(), pf.getImageableY());

            int start = pageIndex * lpp;
            int end = Math.min(start + lpp, lines.size());
            for (int i = start; i < end; i++) {
                g2.drawString(lines.get(i), 0, (i - start + 1) * lineH - fm.getDescent());
            }
            return Printable.PAGE_EXISTS;
        }, format);

        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException e) {
                JOptionPane.showMessageDialog(parent,
                        "Print failed: " + e.getMessage(),
                        "Print Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Save as PDF (pure-Java minimal PDF writer)
    // -------------------------------------------------------------------------

    /**
     * Saves {@code receiptContent} as a minimal PDF at {@code outputPath}.
     * Uses only built-in Java — no external libraries required.
     */
    public static void saveAsPdf(Component parent, String receiptContent, String outputPath) {
        try {
            List<String> lines = wrapLines(receiptContent);

            // Replace box-drawing characters with ASCII equivalents (PDF Courier = Latin-1)
            List<String> safeLines = new ArrayList<>();
            for (String l : lines) {
                safeLines.add(toAsciiSafe(l));
            }

            byte[] pdf = buildPdf(safeLines);
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                fos.write(pdf);
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(outputPath));
            } else {
                JOptionPane.showMessageDialog(parent,
                        "PDF saved:\n" + outputPath,
                        "PDF Saved", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent,
                    "PDF save failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Minimal PDF builder
    // -------------------------------------------------------------------------

    /**
     * Generates a valid, minimal PDF from a list of text lines.
     * Uses Courier (built-in Type1 font), 6 pt, US Letter page, 0.125-inch margins (~119 lines/page).
     *
     * <p>PDF object layout (n = number of pages):
     * <pre>
     *   1          = Catalog
     *   2          = Pages
     *   3..2+n     = Page objects
     *   3+n..2+2n  = Content streams
     *   3+2n       = Font
     * </pre>
     */
    private static byte[] buildPdf(List<String> lines) throws IOException {
        // Page geometry (points: 1 pt = 1/72 inch)
        // 0.125-inch margins → 9 pt; 6 pt font, 6.5 pt leading → ≈ 119 lines/page
        int pageW = 612, pageH = 792;
        float leftX = 18f, topY = 783f, lineH = 6.5f;
        int linesPerPage = (int) ((topY - 9f) / lineH); // ≈ 119 lines per page

        // Split into pages
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += linesPerPage) {
            pages.add(new ArrayList<>(lines.subList(i, Math.min(i + linesPerPage, lines.size()))));
        }
        if (pages.isEmpty()) pages.add(new ArrayList<>());

        int n = pages.size();
        // Object ID assignments
        int catalogId   = 1;
        int pagesId     = 2;
        int firstPageId = 3;
        int firstContId = 3 + n;
        int fontId      = 3 + 2 * n;
        int totalObjs   = fontId + 1; // IDs 1..fontId

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Long> offsets = new ArrayList<>(); // offsets[i] = byte offset of object (i+1)

        // PDF header
        write(out, "%PDF-1.4\n");

        // Object 1: Catalog
        offsets.add((long) out.size());
        writeObj(out, catalogId, "<< /Type /Catalog /Pages 2 0 R >>");

        // Object 2: Pages (kids list built up front)
        StringBuilder kids = new StringBuilder();
        for (int p = 0; p < n; p++) {
            if (p > 0) kids.append(' ');
            kids.append(firstPageId + p).append(" 0 R");
        }
        offsets.add((long) out.size());
        writeObj(out, pagesId,
                "<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>");

        // Objects 3..2+n: Page objects
        for (int p = 0; p < n; p++) {
            int pageObjId = firstPageId + p;
            int contObjId = firstContId + p;
            offsets.add((long) out.size());
            writeObj(out, pageObjId,
                    "<< /Type /Page /Parent 2 0 R"
                    + " /MediaBox [0 0 " + pageW + " " + pageH + "]"
                    + " /Contents " + contObjId + " 0 R"
                    + " /Resources << /Font << /F1 " + fontId + " 0 R >> >> >>");
        }

        // Objects 3+n..2+2n: Content streams
        for (int p = 0; p < n; p++) {
            int contObjId = firstContId + p;
            List<String> pageLines = pages.get(p);

            StringBuilder cs = new StringBuilder();
            cs.append("BT\n");
            cs.append("/F1 ").append((int) FONT_SIZE).append(" Tf\n");
            cs.append(leftX).append(' ').append(topY).append(" Td\n");
            cs.append(lineH).append(" TL\n");
            for (String line : pageLines) {
                cs.append('(').append(escapePdfString(line)).append(") Tj T*\n");
            }
            cs.append("ET\n");

            byte[] csBytes = cs.toString().getBytes(StandardCharsets.ISO_8859_1);
            offsets.add((long) out.size());
            writeStreamObj(out, contObjId, csBytes);
        }

        // Font object
        offsets.add((long) out.size());
        writeObj(out, fontId,
                "<< /Type /Font /Subtype /Type1 /BaseFont /Courier"
                + " /Encoding /WinAnsiEncoding >>");

        // Cross-reference table
        long xrefOffset = out.size();
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n");
        xref.append("0 ").append(totalObjs).append('\n');
        xref.append("0000000000 65535 f \n"); // object 0 (free)
        for (int i = 0; i < offsets.size(); i++) {
            xref.append(String.format("%010d 00000 n \n", offsets.get(i)));
        }
        write(out, xref.toString());

        // Trailer
        write(out, "trailer\n<< /Size " + totalObjs + " /Root 1 0 R >>\n"
                + "startxref\n" + xrefOffset + "\n%%EOF\n");

        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void writeObj(ByteArrayOutputStream out, int id, String dict) throws IOException {
        write(out, id + " 0 obj\n" + dict + "\nendobj\n");
    }

    private static void writeStreamObj(ByteArrayOutputStream out, int id, byte[] content)
            throws IOException {
        write(out, id + " 0 obj\n<< /Length " + content.length + " >>\nstream\n");
        out.write(content);
        write(out, "\nendstream\nendobj\n");
    }

    /** Replaces box-drawing Unicode with plain ASCII so Courier (Latin-1) can render them. */
    private static String toAsciiSafe(String s) {
        return s.replace("\u2554", "+").replace("\u2557", "+")
                .replace("\u255a", "+").replace("\u255d", "+")
                .replace("\u2550", "=").replace("\u2551", "|")
                .replace("\u2502", "|").replace("\u2500", "-")
                .replace("\u250c", "+").replace("\u2510", "+")
                .replace("\u2514", "+").replace("\u2518", "+");
    }

    /** Escapes characters that have special meaning inside a PDF literal string. */
    private static String escapePdfString(String s) {
        return s.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    /** Word-wraps / hard-wraps lines to {@link #CHARS_PER_LINE} columns. */
    private static List<String> wrapLines(String content) {
        List<String> result = new ArrayList<>();
        for (String line : content.split("\n", -1)) {
            if (line.length() <= CHARS_PER_LINE) {
                result.add(line);
            } else {
                while (line.length() > CHARS_PER_LINE) {
                    result.add(line.substring(0, CHARS_PER_LINE));
                    line = line.substring(CHARS_PER_LINE);
                }
                result.add(line);
            }
        }
        return result;
    }
}
