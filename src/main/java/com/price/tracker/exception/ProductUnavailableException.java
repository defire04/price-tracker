package com.price.tracker.exception;

/**
 * The page loaded fine but the product has no price right now (e.g. no shop offers on an
 * aggregator). A price check records it as out of stock instead of reporting a parser failure.
 */
public class ProductUnavailableException extends ProductParsingException {

    public ProductUnavailableException(String message) {
        super(message);
    }
}
