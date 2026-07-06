package com.price.tracker.domain.service.tracking;

import com.price.tracker.domain.entity.PriceSnapshot;
import com.price.tracker.domain.entity.TrackedItem;

import java.util.List;
import java.util.Optional;

public interface TrackingService {

    ProductPreview preview(String url);

    TrackedItem createTracking(long telegramChatId, NewTrackingCommand command);

    List<TrackedItem> listActive(long telegramChatId);

    List<TrackedItem> allActiveWithEvents();

    Optional<TrackedItem> findActiveItem(long itemId);

    List<TrackedItem> listInactive(long telegramChatId);

    TrackingDetails details(long telegramChatId, long itemId);

    List<PriceSnapshot> priceHistory(long telegramChatId, long itemId);

    void rename(long telegramChatId, long itemId, String newName);

    void deactivate(long telegramChatId, long itemId);

    void resume(long telegramChatId, long itemId);
}
