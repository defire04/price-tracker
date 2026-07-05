package com.price.tracker.exception;

public class UnsupportedSiteException extends RuntimeException {

    public UnsupportedSiteException(String urlOrSite) {
        super("No parser for: " + urlOrSite);
    }
}
