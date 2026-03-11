package com.cardpricer.model;

/** Holds the three components of a user-typed card code. */
public record ParsedCode(
        /** Scryfall-compatible set code (e.g. {@code "tdm"} or {@code "plst"}). */
        String setCode,
        /** Scryfall-compatible collector number (e.g. {@code "3"} or {@code "arb-1"}). */
        String collectorNumber,
        /** Finish indicator: {@code ""} (normal), {@code "F"} (foil), {@code "E"} (etched), or {@code "S"} (surge foil). */
        String finish) {}
