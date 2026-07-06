package com.price.tracker.domain.service.check.impl;

import com.price.tracker.domain.entity.PriceSnapshot;
import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.entity.TrackedItemEvent;
import com.price.tracker.domain.entity.User;
import com.price.tracker.domain.enums.TrackedEventType;
import com.price.tracker.domain.model.PriceAlert;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.service.notification.NotificationSender;
import com.price.tracker.domain.service.snapshot.PriceSnapshotService;
import com.price.tracker.domain.service.tracking.TrackingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemCheckProcessorTest {

    private static final long ITEM_ID = 10L;
    private static final long CHAT_ID = 777L;
    private static final String SIZE = "M";

    @Mock
    private TrackingService trackingService;
    @Mock
    private PriceSnapshotService priceSnapshotService;
    @Mock
    private NotificationSender notificationSender;

    private ItemCheckProcessor processor;

    private TrackedItem anItem(TrackedEventType... eventTypes) {
        User user = new User();
        user.setTelegramChatId(CHAT_ID);
        TrackedItem item = new TrackedItem();
        item.setId(ITEM_ID);
        item.setUser(user);
        item.setSizeLabel(SIZE);
        item.setTitle("Test product");
        item.setOriginalPrice(new BigDecimal("1000"));
        for (TrackedEventType type : eventTypes) {
            TrackedItemEvent event = new TrackedItemEvent();
            event.setItem(item);
            event.setEventType(type);
            item.getEvents().add(event);
        }
        return item;
    }

    private PriceSnapshot previousSnapshot(String price, boolean inStock) {
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setPrice(new BigDecimal(price));
        snapshot.setInStock(inStock);
        snapshot.setCheckedAt(Instant.now());
        return snapshot;
    }

    private ProductSnapshot freshProduct(String price, boolean inStock) {
        SizeOffer offer = new SizeOffer(SIZE, new BigDecimal(price), null, inStock);
        return new ProductSnapshot("Test product", offer.price(), null, inStock, List.of(offer));
    }

    private void givenItemAndPrevious(TrackedItem item, PriceSnapshot previous) {
        when(trackingService.findActiveItem(ITEM_ID)).thenReturn(Optional.of(item));
        when(priceSnapshotService.lastOf(ITEM_ID)).thenReturn(Optional.ofNullable(previous));
        processor = new ItemCheckProcessor(trackingService, priceSnapshotService, notificationSender);
    }

    @Test
    void when_backInStock_notifiesRestockAndDeactivatesItem() {
        TrackedItem item = anItem(TrackedEventType.RESTOCK);
        givenItemAndPrevious(item, previousSnapshot("900", false));

        processor.process(ITEM_ID, freshProduct("900", true));

        ArgumentCaptor<PriceAlert> alert = ArgumentCaptor.forClass(PriceAlert.class);
        verify(notificationSender).send(eq(CHAT_ID), alert.capture());
        assertThat(alert.getValue().type()).isEqualTo(TrackedEventType.RESTOCK);
        assertThat(item.getEvents().getFirst().isActive()).isFalse();
        assertThat(item.isActive()).isFalse();
    }

    @Test
    void when_priceDropsWithoutThreshold_notifies() {
        TrackedItem item = anItem(TrackedEventType.PRICE_DROP);
        givenItemAndPrevious(item, previousSnapshot("1000", true));

        processor.process(ITEM_ID, freshProduct("990", true));

        ArgumentCaptor<PriceAlert> alert = ArgumentCaptor.forClass(PriceAlert.class);
        verify(notificationSender).send(eq(CHAT_ID), alert.capture());
        assertThat(alert.getValue().type()).isEqualTo(TrackedEventType.PRICE_DROP);
        assertThat(alert.getValue().newPrice()).isEqualByComparingTo(new BigDecimal("990"));
    }

    @Test
    void when_priceDropBelowThreshold_staysSilent() {
        TrackedItem item = anItem(TrackedEventType.PRICE_DROP);
        item.setDropThreshold(new BigDecimal("5"));
        givenItemAndPrevious(item, previousSnapshot("1000", true));

        processor.process(ITEM_ID, freshProduct("980", true));

        verify(notificationSender, never()).send(eq(CHAT_ID), any());
    }

    @Test
    void when_priceDropReachesThreshold_notifies() {
        TrackedItem item = anItem(TrackedEventType.PRICE_DROP);
        item.setDropThreshold(new BigDecimal("5"));
        givenItemAndPrevious(item, previousSnapshot("1000", true));

        processor.process(ITEM_ID, freshProduct("900", true));

        verify(notificationSender).send(eq(CHAT_ID), any(PriceAlert.class));
    }

    @Test
    void when_discountCrossesThreshold_notifiesOnce() {
        TrackedItem item = anItem(TrackedEventType.DISCOUNT_PERCENT);
        item.setDiscountThreshold(new BigDecimal("30"));
        givenItemAndPrevious(item, previousSnapshot("900", true));

        processor.process(ITEM_ID, freshProduct("600", true));

        ArgumentCaptor<PriceAlert> alert = ArgumentCaptor.forClass(PriceAlert.class);
        verify(notificationSender).send(eq(CHAT_ID), alert.capture());
        assertThat(alert.getValue().type()).isEqualTo(TrackedEventType.DISCOUNT_PERCENT);
        assertThat(alert.getValue().discountPercent()).isEqualByComparingTo(new BigDecimal("40"));
    }

    @Test
    void when_discountAlreadyAboveThreshold_staysSilent() {
        TrackedItem item = anItem(TrackedEventType.DISCOUNT_PERCENT);
        item.setDiscountThreshold(new BigDecimal("30"));
        givenItemAndPrevious(item, previousSnapshot("600", true));

        processor.process(ITEM_ID, freshProduct("590", true));

        verify(notificationSender, never()).send(eq(CHAT_ID), any());
    }

    @Test
    void when_trackedSizeVanished_savesOutOfStockSnapshotWithLastPrice() {
        TrackedItem item = anItem(TrackedEventType.PRICE_DROP);
        givenItemAndPrevious(item, previousSnapshot("1000", true));
        SizeOffer otherSizeOnly = new SizeOffer("XXL", new BigDecimal("500"), null, true);
        ProductSnapshot fresh = new ProductSnapshot("Test product", otherSizeOnly.price(), null, true, List.of(otherSizeOnly));

        processor.process(ITEM_ID, fresh);

        ArgumentCaptor<PriceSnapshot> saved = ArgumentCaptor.forClass(PriceSnapshot.class);
        verify(priceSnapshotService).record(saved.capture());
        assertThat(saved.getValue().isInStock()).isFalse();
        assertThat(saved.getValue().getPrice()).isEqualByComparingTo(new BigDecimal("1000"));
    }
}
