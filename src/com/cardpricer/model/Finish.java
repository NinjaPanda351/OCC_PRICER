package com.cardpricer.model;
public enum Finish {
    NORMAL(""), FOIL("F"), ETCHED("E"), SURGE("S");
    private final String code;
    Finish(String code) { this.code = code; }
    public String code() { return code; }
    public static Finish fromCode(String code) {
        for (Finish finish : values()) if (finish.code.equalsIgnoreCase(code)) return finish;
        throw new IllegalArgumentException("Unknown finish: " + code);
    }
}
