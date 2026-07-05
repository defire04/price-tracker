package com.price.tracker.domain.service.notification;

import com.price.tracker.domain.model.PriceAlert;

public interface NotificationSender {

    void send(long telegramChatId, PriceAlert alert);
}
