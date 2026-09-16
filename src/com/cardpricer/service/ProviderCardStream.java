package com.cardpricer.service;

import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.IOException;
import java.io.Reader;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Streams individual objects from JSON arrays or JSONL without retaining the bulk document. */
final class ProviderCardStream {
    private ProviderCardStream() {}
    static void read(Reader reader, BooleanSupplier cancelled, Consumer<JSONObject> consume) throws IOException, InterruptedException {
        JSONTokener tokens = new JSONTokener(reader);
        try {
            char first = tokens.nextClean();
            boolean array = first == '[';
            if (!array) tokens.back();
            while (true) {
                if (cancelled.getAsBoolean()) throw new InterruptedException("Catalog refresh cancelled");
                char next = tokens.nextClean();
                if (array && next == ']') {
                    if (tokens.nextClean() != 0) throw new IOException("Unexpected data after catalog array");
                    return;
                }
                if (!array && next == 0) return;
                if (next != '{') throw new IOException("Expected a complete catalog object");
                tokens.back();
                Object value = tokens.nextValue();
                if (!(value instanceof JSONObject card)) throw new IOException("Expected a catalog object");
                consume.accept(card);
                if (array) {
                    char delimiter = tokens.nextClean();
                    if (delimiter == ']') {
                        if (tokens.nextClean() != 0) throw new IOException("Unexpected data after catalog array");
                        return;
                    }
                    if (delimiter != ',') throw new IOException("Truncated or invalid catalog array");
                    char following = tokens.nextClean();
                    if (following != '{') throw new IOException("Expected a catalog object after comma");
                    tokens.back();
                }
            }
        } catch (RuntimeException failure) { throw new IOException("Invalid downloaded catalog record", failure); }
    }
}
