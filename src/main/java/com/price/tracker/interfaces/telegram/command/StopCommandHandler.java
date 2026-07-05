package com.price.tracker.interfaces.telegram.command;

import com.price.tracker.domain.service.tracking.TrackingService;
import com.price.tracker.exception.TrackingNotFoundException;
import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StopCommandHandler implements CommandHandler {

    private final TrackingService trackingService;
    private final TelegramReplySender replySender;

    @Override
    public String command() {
        return "/stop";
    }

    @Override
    public void handle(long chatId, String arguments) {
        Long itemId = parseItemId(arguments);
        if (itemId == null) {
            replySender.sendText(chatId, "Надішли так: /stop <id>. Список: /list");
            return;
        }
        try {
            trackingService.deactivate(chatId, itemId);
            replySender.sendText(chatId, "⏹ Відстеження #" + itemId + " зупинено");
        } catch (TrackingNotFoundException e) {
            replySender.sendText(chatId, "Відстеження #" + itemId + " не знайдено");
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
