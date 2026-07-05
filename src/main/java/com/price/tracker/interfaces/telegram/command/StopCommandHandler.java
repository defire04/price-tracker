package com.price.tracker.interfaces.telegram.command;

import com.price.tracker.domain.service.tracking.TrackingService;
import com.price.tracker.exception.TrackingNotFoundException;
import com.price.tracker.interfaces.telegram.i18n.ChatLocales;
import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import com.price.tracker.interfaces.telegram.view.TrackerMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class StopCommandHandler implements CommandHandler {

    private final TrackingService trackingService;
    private final TelegramReplySender replySender;
    private final TrackerMessages messages;
    private final ChatLocales chatLocales;

    @Override
    public String command() {
        return "/stop";
    }

    @Override
    public void handle(long chatId, String arguments) {
        Locale locale = chatLocales.localeOf(chatId);
        Long itemId = parseItemId(arguments);
        if (itemId == null) {
            replySender.sendText(chatId, messages.stopUsage(locale));
            return;
        }
        try {
            trackingService.deactivate(chatId, itemId);
            replySender.sendText(chatId, messages.stopDone(locale));
        } catch (TrackingNotFoundException e) {
            replySender.sendText(chatId, messages.stopNotFound(locale));
        }
    }

    private Long parseItemId(String arguments) {
        try {
            return Long.parseLong(arguments.trim().replace("#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
