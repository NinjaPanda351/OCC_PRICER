package com.cardpricer.model;
public record PrintingIdentity(String providerId, String set, String collectorNumber, String language) {
    public PrintingIdentity {
        if (set == null || set.isBlank() || collectorNumber == null || collectorNumber.isBlank())
            throw new IllegalArgumentException("Printing identity is incomplete");
    }
}
