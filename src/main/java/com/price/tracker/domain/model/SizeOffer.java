package com.price.tracker.domain.model;

import java.math.BigDecimal;

public record SizeOffer(String label, BigDecimal price, BigDecimal regularPrice, boolean inStock) {

    public static final String ONE_SIZE_LABEL = "ONE_SIZE";
}
