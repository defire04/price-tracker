package com.price.tracker.domain.service.user;

import com.price.tracker.domain.entity.User;
import com.price.tracker.domain.model.TelegramProfile;

public interface UserService {

    User registerIfAbsent(long telegramChatId);

    User registerOrUpdate(long telegramChatId, TelegramProfile profile);

    String resolveLanguage(long telegramChatId);

    void setPreferredLanguage(long telegramChatId, String language);
}
