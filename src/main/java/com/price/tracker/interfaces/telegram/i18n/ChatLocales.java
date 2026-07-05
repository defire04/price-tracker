package com.price.tracker.interfaces.telegram.i18n;

import com.price.tracker.domain.service.user.UserService;
import com.price.tracker.util.BotLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ChatLocales {

    private final UserService userService;

    public Locale localeOf(long chatId) {
        return BotLanguage.toLocale(userService.resolveLanguage(chatId));
    }
}
