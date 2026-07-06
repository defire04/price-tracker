package com.price.tracker.domain.service.check.impl;

import com.price.tracker.config.TrackerProperties;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.enums.Site;
import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.service.tracking.TrackingService;
import com.price.tracker.domain.service.check.PriceCheckService;
import com.price.tracker.domain.service.notification.AdminNotifier;
import com.price.tracker.domain.service.parser.SiteParser;
import com.price.tracker.domain.service.parser.SiteParserRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceCheckServiceImpl implements PriceCheckService {

    private final TrackingService trackingService;
    private final SiteParserRegistry parserRegistry;
    private final ItemCheckProcessor itemCheckProcessor;
    private final TrackerProperties trackerProperties;
    private final AdminNotifier adminNotifier;

    @Override
    public void runCheckCycle() {
        List<TrackedItem> items = trackingService.allActiveWithEvents();
        Map<Site, Map<String, List<TrackedItem>>> itemsBySiteAndUrl = items.stream()
                .collect(Collectors.groupingBy(TrackedItem::getSite, Collectors.groupingBy(TrackedItem::getUrl)));
        log.info("Price check started: {} items", items.size());
        itemsBySiteAndUrl.forEach(this::checkSite);
        log.info("Price check finished");
    }

    @Override
    public boolean checkItemNow(long itemId) {
        TrackedItem item = trackingService.findActiveItem(itemId).orElse(null);
        if (item == null) {
            return false;
        }
        SiteParser parser = parserRegistry.parserFor(item.getSite());
        ProductSnapshot freshProduct;
        try {
            freshProduct = parser.fetch(item.getUrl());
        } catch (Exception e) {
            log.error("On-demand check failed for item {} url {}", itemId, item.getUrl(), e);
            adminNotifier.parserFailed(item.getUrl(), e.toString());
            return false;
        }
        processItemSafely(itemId, freshProduct);
        return true;
    }

    private void checkSite(Site site, Map<String, List<TrackedItem>> itemsByUrl) {
        SiteParser parser = parserRegistry.parserFor(site);
        boolean firstUrl = true;
        for (Map.Entry<String, List<TrackedItem>> urlGroup : itemsByUrl.entrySet()) {
            if (!firstUrl) {
                sleepJitter();
            }
            firstUrl = false;
            checkUrl(parser, urlGroup.getKey(), urlGroup.getValue());
        }
    }

    private void checkUrl(SiteParser parser, String url, List<TrackedItem> items) {
        ProductSnapshot freshProduct;
        try {
            freshProduct = parser.fetch(url);
        } catch (Exception e) {
            log.error("Price check failed for url {}", url, e);
            adminNotifier.parserFailed(url, e.toString());
            return;
        }
        for (TrackedItem item : items) {
            processItemSafely(item.getId(), freshProduct);
        }
    }

    private void processItemSafely(long itemId, ProductSnapshot freshProduct) {
        try {
            itemCheckProcessor.process(itemId, freshProduct);
        } catch (Exception e) {
            log.error("Price check failed for item {}", itemId, e);
        }
    }

    private void sleepJitter() {
        long delayMs = ThreadLocalRandom.current().nextLong(
                trackerProperties.check().jitterMinMs(),
                trackerProperties.check().jitterMaxMs() + 1);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
