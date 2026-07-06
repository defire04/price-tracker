package com.price.tracker.interfaces.telegram.admin;

import com.price.tracker.config.TrackerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminAccess {

    private final TrackerProperties trackerProperties;

    public boolean isAdmin(long chatId) {
        long adminChatId = trackerProperties.bot().adminChatId();
        return adminChatId != 0 && adminChatId == chatId;
    }
}
