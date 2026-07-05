package com.price.tracker.domain.model;

import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.enums.TrackedEventType;

import java.math.BigDecimal;

public record PriceAlert(TrackedItem item,
                         TrackedEventType type,
                         BigDecimal oldPrice,
                         BigDecimal newPrice,
                         BigDecimal discountPercent) {
}
