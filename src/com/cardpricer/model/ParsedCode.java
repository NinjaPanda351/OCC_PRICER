package com.cardpricer.model;

/** Holds the three components of a user-typed card code. */
public class ParsedCode {
    public final String setCode;         // e.g. "TDM"
    public final String collectorNumber; // e.g. "3"
    public final String finish;          // "", "F", or "E"

    public ParsedCode(String setCode, String collectorNumber, String finish) {
        this.setCode = setCode;
        this.collectorNumber = collectorNumber;
        this.finish = finish;
    }
}
