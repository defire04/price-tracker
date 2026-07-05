package com.price.tracker.domain.service.parser;

import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.enums.Site;

public interface SiteParser {

    Site site();

    ProductSnapshot fetch(String url);
}
