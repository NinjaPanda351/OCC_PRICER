package com.cardpricer.service;

import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared CSV quoting and locale-independent decimal encoding. */
public final class CsvRows {
    private CsvRows() {}

    public static String row(Object... fields) {
        return CSVFormat.RFC4180.format(fields);
    }

    public static void write(Writer writer, Object... fields) throws IOException {
        writeRow(writer, row(fields));
    }

    public static void writeRow(Writer writer, String row) throws IOException {
        writer.write(row);
        writer.write("\r\n");
    }

    public static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
