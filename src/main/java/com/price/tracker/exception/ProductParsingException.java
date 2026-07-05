package com.price.tracker.exception;

public class ProductParsingException extends RuntimeException {

    public ProductParsingException(String message) {
        super(message);
    }

    public ProductParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
