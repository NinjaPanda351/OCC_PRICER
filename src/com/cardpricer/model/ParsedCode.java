package com.cardpricer.model;

/** Holds the three components of a user-typed card code. */
public class ParsedCode {
    /** Scryfall-compatible set code (e.g. {@code "tdm"} or {@code "plst"}). */
    public final String setCode;
    /** Scryfall-compatible collector number (e.g. {@code "3"} or {@code "arb-1"}). */
    public final String collectorNumber;
    /** Finish indicator: {@code ""} (normal), {@code "F"} (foil), {@code "E"} (etched), or {@code "S"} (surge foil). */
    public final String finish;

    /**
     * Creates a ParsedCode with all three components.
     *
     * @param setCode         Scryfall-compatible set code
     * @param collectorNumber Scryfall-compatible collector number
     * @param finish          finish indicator: {@code ""}, {@code "F"}, {@code "E"}, or {@code "S"}
     */
    public ParsedCode(String setCode, String collectorNumber, String finish) {
        this.setCode = setCode;
        this.collectorNumber = collectorNumber;
        this.finish = finish;
    }
}
