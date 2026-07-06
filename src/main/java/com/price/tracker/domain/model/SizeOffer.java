package com.price.tracker.domain.model;

import java.math.BigDecimal;

/**
 * One selectable variant of a product. For clothing shops it is a size;
 * for aggregators (EK) it is a shop offer: label = shop name, url = shop link.
 */
public record SizeOffer(String label, BigDecimal price, BigDecimal regularPrice, boolean inStock, String url) {

    public static final String ONE_SIZE_LABEL = "ONE_SIZE";
    public static final String MIN_PRICE_LABEL = "MIN_PRICE";

    public SizeOffer(String label, BigDecimal price, BigDecimal regularPrice, boolean inStock) {
        this(label, price, regularPrice, inStock, null);
    }
}
