package com.price.tracker.domain.service.tracking;

import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.enums.Site;
import com.price.tracker.domain.enums.TrackedEventType;

import java.math.BigDecimal;
import java.util.Set;

public record NewTrackingCommand(String url,
                                 Site site,
                                 ProductSnapshot product,
                                 String sizeLabel,
                                 Set<TrackedEventType> eventTypes,
                                 BigDecimal discountThreshold,
                                 BigDecimal dropThreshold) {
}
