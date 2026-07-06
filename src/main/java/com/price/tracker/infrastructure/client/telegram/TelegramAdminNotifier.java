package com.price.tracker.infrastructure.client.telegram;

import com.price.tracker.config.TrackerProperties;
import com.price.tracker.domain.entity.User;
import com.price.tracker.domain.repository.UserRepository;
import com.price.tracker.domain.service.notification.AdminNotifier;
import com.price.tracker.util.BotLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramAdminNotifier implements AdminNotifier {

    // Playwright exceptions carry a full call log - keep the alert readable
    private static final int MAX_ERROR_LENGTH = 300;

    private final TelegramClient telegramClient;
    private final MessageSource messageSource;
    private final UserRepository userRepository;
    private final TrackerProperties trackerProperties;

    @Override
    public void parserFailed(String url, String error) {
        long adminChatId = trackerProperties.bot().adminChatId();
        if (adminChatId == 0) {
            return;
        }
        String text = messageSource.getMessage("admin.parserFailed",
                new Object[]{url, truncate(error)}, adminLocale(adminChatId));
        try {
            telegramClient.execute(SendMessage.builder().chatId(adminChatId).text(text).build());
        } catch (TelegramApiException e) {
            log.error("Failed to send admin alert to chat {}", adminChatId, e);
        }
    }

    private Locale adminLocale(long adminChatId) {
        User admin = userRepository.findByTelegramChatId(adminChatId).orElse(null);
        if (admin == null) {
            return BotLanguage.toLocale(BotLanguage.UKRAINIAN);
        }
        return BotLanguage.toLocale(BotLanguage.resolve(admin.getPreferredLanguage(), admin.getLanguageCode()));
    }

    private String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return String.valueOf(error);
        }
        return error.substring(0, MAX_ERROR_LENGTH) + "…";
    }
}
