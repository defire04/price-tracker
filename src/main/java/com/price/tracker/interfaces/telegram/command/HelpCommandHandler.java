package com.price.tracker.interfaces.telegram.command;

import com.price.tracker.interfaces.telegram.i18n.ChatLocales;
import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import com.price.tracker.interfaces.telegram.view.TrackerMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class HelpCommandHandler implements CommandHandler {

    private final TelegramReplySender replySender;
    private final TrackerMessages messages;
    private final ChatLocales chatLocales;

    @Override
    public String command() {
        return "/help";
    }

    @Override
    public void handle(long chatId, String arguments) {
        Locale locale = chatLocales.localeOf(chatId);
        replySender.sendKeyboard(chatId, messages.helpText(locale), messages.backToMenuKeyboard(locale));
    }
}
