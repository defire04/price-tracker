package com.price.tracker.domain.service.tracking;

import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.enums.Site;

public record ProductPreview(Site site, ProductSnapshot product) {
}
