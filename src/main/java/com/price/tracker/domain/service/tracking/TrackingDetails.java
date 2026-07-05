package com.price.tracker.domain.service.tracking;

import com.price.tracker.domain.entity.PriceSnapshot;
import com.price.tracker.domain.entity.TrackedItem;

public record TrackingDetails(TrackedItem item, PriceSnapshot lastSnapshot) {
}
