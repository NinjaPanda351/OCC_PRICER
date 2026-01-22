package com.cardpricer.exception;

public class ScryfallApiException extends Exception {
    public ScryfallApiException(String message) {
        super(message);
    }

    public ScryfallApiException(String message, Throwable cause) {
        super(message, cause);
    }
}