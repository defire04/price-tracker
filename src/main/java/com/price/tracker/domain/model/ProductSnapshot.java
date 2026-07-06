package com.price.tracker.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record ProductSnapshot(String title,
                              BigDecimal price,
                              BigDecimal regularPrice,
                              boolean inStock,
                              List<SizeOffer> sizes,
                              AggregateStats stats,
                              List<VariantGroup> variants) {

    /**
     * Market overview from aggregator sites (EK, Hotline); null for regular shops.
     * avgPrice may be null when the site does not expose it.
     */
    public record AggregateStats(BigDecimal minPrice, BigDecimal avgPrice, BigDecimal maxPrice, int offerCount) {
    }

    /**
     * Product variants exposed by the site (color, modification, ...); picking an
     * option means restarting the preview from its url. Empty for simple products.
     */
    public record VariantGroup(String name, List<VariantOption> options) {
    }

    public record VariantOption(String label, String url, boolean selected) {

        public static final String ANY_LABEL = "ANY";
    }

    public ProductSnapshot(String title, BigDecimal price, BigDecimal regularPrice, boolean inStock,
                           List<SizeOffer> sizes) {
        this(title, price, regularPrice, inStock, sizes, null, List.of());
    }

    public ProductSnapshot(String title, BigDecimal price, BigDecimal regularPrice, boolean inStock,
                           List<SizeOffer> sizes, AggregateStats stats) {
        this(title, price, regularPrice, inStock, sizes, stats, List.of());
    }

    public Optional<SizeOffer> findSize(String sizeLabel) {
        if (sizeLabel == null) {
            return sizes.isEmpty() ? Optional.empty() : Optional.of(sizes.getFirst());
        }
        return sizes.stream()
                .filter(size -> size.label().equals(sizeLabel))
                .findFirst();
    }
}
