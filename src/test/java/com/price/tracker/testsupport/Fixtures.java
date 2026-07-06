package com.price.tracker.testsupport;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Saved live pages of the supported sites. A red parser test against a frozen
 * fixture means the parser broke, not the site.
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static Document page(String name, String baseUrl) {
        try (InputStream input = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (input == null) {
                throw new IllegalArgumentException("No fixture " + name);
            }
            return Jsoup.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8), baseUrl);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
