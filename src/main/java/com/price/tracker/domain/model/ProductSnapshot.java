package com.price.tracker.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record ProductSnapshot(String title,
                              BigDecimal price,
                              BigDecimal regularPrice,
                              boolean inStock,
                              List<SizeOffer> sizes) {

    public Optional<SizeOffer> findSize(String sizeLabel) {
        if (sizeLabel == null) {
            return sizes.isEmpty() ? Optional.empty() : Optional.of(sizes.getFirst());
        }
        return sizes.stream()
                .filter(size -> size.label().equals(sizeLabel))
                .findFirst();
    }
}
