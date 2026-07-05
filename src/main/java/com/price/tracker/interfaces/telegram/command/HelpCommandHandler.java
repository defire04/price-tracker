package com.price.tracker.interfaces.telegram.command;

import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import com.price.tracker.interfaces.telegram.view.TrackerMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HelpCommandHandler implements CommandHandler {

    private final TelegramReplySender replySender;

    @Override
    public String command() {
        return "/help";
    }

    @Override
    public void handle(long chatId, String arguments) {
        replySender.sendKeyboard(chatId, TrackerMessages.helpText(), TrackerMessages.backToMenuKeyboard());
    }
}
