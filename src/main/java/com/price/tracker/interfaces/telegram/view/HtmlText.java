package com.price.tracker.interfaces.telegram.view;

/**
 * Escaping for the HTML parse mode of menu messages. Applies to every dynamic
 * value (titles, labels, urls); static bundle texts must stay HTML-safe.
 */
public final class HtmlText {

    private HtmlText() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
