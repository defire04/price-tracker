package com.price.tracker.domain.service.tracking.impl;

import com.price.tracker.domain.entity.PriceSnapshot;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.enums.Site;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.enums.TrackedEventType;
import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.entity.TrackedItemEvent;
import com.price.tracker.domain.entity.User;
import com.price.tracker.domain.repository.PriceSnapshotRepository;
import com.price.tracker.domain.repository.TrackedItemRepository;
import com.price.tracker.domain.service.parser.SiteParserRegistry;
import com.price.tracker.domain.service.tracking.NewTrackingCommand;
import com.price.tracker.domain.service.tracking.ProductPreview;
import com.price.tracker.domain.service.tracking.TrackingDetails;
import com.price.tracker.domain.service.tracking.TrackingService;
import com.price.tracker.domain.service.user.UserService;
import com.price.tracker.exception.ProductParsingException;
import com.price.tracker.exception.TrackingNotFoundException;
import com.price.tracker.exception.UnsupportedSiteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrackingServiceImpl implements TrackingService {

    private final SiteParserRegistry parserRegistry;
    private final UserService userService;
    private final TrackedItemRepository trackedItemRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;

    @Override
    public ProductPreview preview(String url) {
        Site site = Site.fromUrl(url).orElseThrow(() -> new UnsupportedSiteException(url));
        ProductSnapshot product = parserRegistry.parserFor(site).fetch(url);
        return new ProductPreview(site, product);
    }

    @Override
    @Transactional
    public TrackedItem createTracking(long telegramChatId, NewTrackingCommand command) {
        User user = userService.registerIfAbsent(telegramChatId);
        SizeOffer chosenSize = command.product().findSize(command.sizeLabel())
                .orElseThrow(() -> new ProductParsingException("Size not found: " + command.sizeLabel()));
        Instant now = Instant.now();
        TrackedItem item = buildItem(user, command, chosenSize, now);
        trackedItemRepository.save(item);
        priceSnapshotRepository.save(buildSnapshot(item, chosenSize, now));
        return item;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackedItem> listActive(long telegramChatId) {
        return trackedItemRepository.findActiveByChatIdWithEvents(telegramChatId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackedItem> listInactive(long telegramChatId) {
        return trackedItemRepository.findTop20ByUserTelegramChatIdAndActiveFalseOrderByIdDesc(telegramChatId);
    }

    @Override
    @Transactional(readOnly = true)
    public TrackingDetails details(long telegramChatId, long itemId) {
        TrackedItem item = trackedItemRepository.findByIdAndChatIdWithEvents(itemId, telegramChatId)
                .orElseThrow(() -> new TrackingNotFoundException(itemId));
        PriceSnapshot lastSnapshot = priceSnapshotRepository.findTopByItemIdOrderByCheckedAtDesc(itemId).orElse(null);
        return new TrackingDetails(item, lastSnapshot);
    }

    @Override
    @Transactional
    public void resume(long telegramChatId, long itemId) {
        TrackedItem item = trackedItemRepository.findByIdAndChatIdWithEvents(itemId, telegramChatId)
                .filter(trackedItem -> !trackedItem.isActive())
                .orElseThrow(() -> new TrackingNotFoundException(itemId));
        item.setActive(true);
        item.getEvents().forEach(event -> event.setActive(true));
    }

    private static final int MAX_DISPLAY_NAME_LENGTH = 128;

    @Override
    @Transactional
    public void rename(long telegramChatId, long itemId, String newName) {
        if (newName == null || newName.isBlank()) {
            return;
        }
        TrackedItem item = trackedItemRepository.findByIdAndUserTelegramChatId(itemId, telegramChatId)
                .filter(TrackedItem::isActive)
                .orElseThrow(() -> new TrackingNotFoundException(itemId));
        String trimmedName = newName.trim();
        if (trimmedName.length() > MAX_DISPLAY_NAME_LENGTH) {
            trimmedName = trimmedName.substring(0, MAX_DISPLAY_NAME_LENGTH);
        }
        item.setDisplayName(trimmedName);
    }

    @Override
    @Transactional
    public void deactivate(long telegramChatId, long itemId) {
        TrackedItem item = trackedItemRepository.findByIdAndUserTelegramChatId(itemId, telegramChatId)
                .filter(TrackedItem::isActive)
                .orElseThrow(() -> new TrackingNotFoundException(itemId));
        item.setActive(false);
    }

    private TrackedItem buildItem(User user, NewTrackingCommand command, SizeOffer chosenSize, Instant now) {
        TrackedItem item = new TrackedItem();
        item.setUser(user);
        item.setUrl(command.url());
        item.setSite(command.site());
        item.setSizeLabel(command.sizeLabel());
        item.setTitle(command.product().title());
        item.setOriginalPrice(chosenSize.regularPrice() != null ? chosenSize.regularPrice() : chosenSize.price());
        item.setDiscountThreshold(command.discountThreshold());
        item.setCreatedAt(now);
        for (TrackedEventType eventType : command.eventTypes()) {
            item.getEvents().add(buildEvent(item, eventType, now));
        }
        return item;
    }

    private TrackedItemEvent buildEvent(TrackedItem item, TrackedEventType eventType, Instant now) {
        TrackedItemEvent event = new TrackedItemEvent();
        event.setItem(item);
        event.setEventType(eventType);
        event.setCreatedAt(now);
        return event;
    }

    private PriceSnapshot buildSnapshot(TrackedItem item, SizeOffer chosenSize, Instant now) {
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setItem(item);
        snapshot.setPrice(chosenSize.price());
        snapshot.setRegularPrice(chosenSize.regularPrice());
        snapshot.setInStock(chosenSize.inStock());
        snapshot.setCheckedAt(now);
        return snapshot;
    }
}
