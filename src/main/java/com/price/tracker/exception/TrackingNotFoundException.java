package com.price.tracker.exception;

public class TrackingNotFoundException extends RuntimeException {

    public TrackingNotFoundException(long itemId) {
        super("Tracking not found: " + itemId);
    }
}
