package com.price.tracker.domain.service.check.impl;

import com.price.tracker.domain.model.PriceAlert;
import com.price.tracker.domain.entity.PriceSnapshot;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.enums.TrackedEventType;
import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.entity.TrackedItemEvent;
import com.price.tracker.domain.service.notification.NotificationSender;
import com.price.tracker.domain.service.snapshot.PriceSnapshotService;
import com.price.tracker.domain.service.tracking.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemCheckProcessor {

    private final TrackingService trackingService;
    private final PriceSnapshotService priceSnapshotService;
    private final NotificationSender notificationSender;

    @Transactional
    public void process(long itemId, ProductSnapshot freshProduct) {
        TrackedItem item = trackingService.findActiveItem(itemId).orElse(null);
        if (item == null) {
            return;
        }
        PriceSnapshot previous = priceSnapshotService.lastOf(itemId).orElse(null);
        SizeOffer current = resolveTrackedSize(item, freshProduct, previous);
        if (previous != null) {
            evaluateEvents(item, previous, current, freshProduct.stats());
        }
        Instant now = Instant.now();
        priceSnapshotService.record(buildSnapshot(item, current, now));
        item.setLastCheckedAt(now);
    }

    private SizeOffer resolveTrackedSize(TrackedItem item, ProductSnapshot freshProduct, PriceSnapshot previous) {
        return freshProduct.findSize(item.getSizeLabel())
                .orElseGet(() -> vanishedSize(item, freshProduct, previous));
    }

    private SizeOffer vanishedSize(TrackedItem item, ProductSnapshot freshProduct, PriceSnapshot previous) {
        BigDecimal lastKnownPrice = previous != null ? previous.getPrice() : freshProduct.price();
        BigDecimal lastKnownRegular = previous != null ? previous.getRegularPrice() : freshProduct.regularPrice();
        return new SizeOffer(item.getSizeLabel(), lastKnownPrice, lastKnownRegular, false);
    }

    private void evaluateEvents(TrackedItem item, PriceSnapshot previous, SizeOffer current,
                                ProductSnapshot.AggregateStats stats) {
        for (TrackedItemEvent event : item.getEvents()) {
            if (!event.isActive()) {
                continue;
            }
            switch (event.getEventType()) {
                case RESTOCK -> checkRestock(item, event, previous, current, stats);
                case PRICE_DROP -> checkPriceDrop(item, previous, current, stats);
                case PRICE_INCREASE -> checkPriceIncrease(item, previous, current, stats);
                case DISCOUNT_PERCENT -> checkDiscountThreshold(item, previous, current, stats);
            }
        }
        boolean anyEventStillActive = item.getEvents().stream().anyMatch(TrackedItemEvent::isActive);
        if (!anyEventStillActive) {
            item.setActive(false);
        }
    }

    private void checkRestock(TrackedItem item, TrackedItemEvent event, PriceSnapshot previous, SizeOffer current,
                              ProductSnapshot.AggregateStats stats) {
        if (!previous.isInStock() && current.inStock()) {
            notify(item, new PriceAlert(item, TrackedEventType.RESTOCK, previous.getPrice(), current.price(), null,
                    current.url(), stats));
            event.setActive(false);
        }
    }

    private void checkPriceDrop(TrackedItem item, PriceSnapshot previous, SizeOffer current,
                                ProductSnapshot.AggregateStats stats) {
        if (current.price().compareTo(previous.getPrice()) >= 0) {
            return;
        }
        if (item.getDropThreshold() != null && dropPercent(previous, current).compareTo(item.getDropThreshold()) < 0) {
            return;
        }
        notify(item, new PriceAlert(item, TrackedEventType.PRICE_DROP, previous.getPrice(), current.price(), null,
                current.url(), stats));
    }

    private BigDecimal dropPercent(PriceSnapshot previous, SizeOffer current) {
        if (previous.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return previous.getPrice().subtract(current.price())
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.getPrice(), 2, RoundingMode.HALF_UP);
    }

    private void checkPriceIncrease(TrackedItem item, PriceSnapshot previous, SizeOffer current,
                                    ProductSnapshot.AggregateStats stats) {
        if (current.price().compareTo(previous.getPrice()) > 0) {
            notify(item, new PriceAlert(item, TrackedEventType.PRICE_INCREASE, previous.getPrice(), current.price(), null,
                    current.url(), stats));
        }
    }

    private void checkDiscountThreshold(TrackedItem item, PriceSnapshot previous, SizeOffer current,
                                        ProductSnapshot.AggregateStats stats) {
        BigDecimal threshold = item.getDiscountThreshold();
        if (threshold == null) {
            return;
        }
        BigDecimal currentDiscount = discountPercent(item, current.regularPrice(), current.price());
        BigDecimal previousDiscount = discountPercent(item, previous.getRegularPrice(), previous.getPrice());
        boolean crossedThreshold = currentDiscount.compareTo(threshold) >= 0
                && previousDiscount.compareTo(threshold) < 0;
        if (crossedThreshold) {
            notify(item, new PriceAlert(item, TrackedEventType.DISCOUNT_PERCENT,
                    previous.getPrice(), current.price(), currentDiscount, current.url(), stats));
        }
    }

    private BigDecimal discountPercent(TrackedItem item, BigDecimal regularPrice, BigDecimal price) {
        BigDecimal base = regularPrice != null ? regularPrice : item.getOriginalPrice();
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return base.subtract(price)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 2, RoundingMode.HALF_UP);
    }

    private void notify(TrackedItem item, PriceAlert alert) {
        try {
            notificationSender.send(item.getUser().getTelegramChatId(), alert);
        } catch (Exception e) {
            log.error("Failed to notify chat about item {}", item.getId(), e);
        }
    }

    private PriceSnapshot buildSnapshot(TrackedItem item, SizeOffer current, Instant now) {
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setItem(item);
        snapshot.setPrice(current.price());
        snapshot.setRegularPrice(current.regularPrice());
        snapshot.setInStock(current.inStock());
        snapshot.setCheckedAt(now);
        return snapshot;
    }
}
