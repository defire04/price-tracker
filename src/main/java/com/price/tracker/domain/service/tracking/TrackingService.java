package com.price.tracker.domain.service.tracking;

import com.price.tracker.domain.entity.TrackedItem;

import java.util.List;

public interface TrackingService {

    ProductPreview preview(String url);

    TrackedItem createTracking(long telegramChatId, NewTrackingCommand command);

    List<TrackedItem> listActive(long telegramChatId);

    TrackingDetails details(long telegramChatId, long itemId);

    void rename(long telegramChatId, long itemId, String newName);

    void deactivate(long telegramChatId, long itemId);
}
