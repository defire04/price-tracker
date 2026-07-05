package com.price.tracker.interfaces.telegram.command;

import com.price.tracker.interfaces.telegram.wizard.TrackWizardFlow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrackCommandHandler implements CommandHandler {

    private final TrackWizardFlow wizardFlow;

    @Override
    public String command() {
        return "/track";
    }

    @Override
    public void handle(long chatId, String arguments) {
        if (arguments.isBlank()) {
            wizardFlow.startAwaitUrlNewMessage(chatId);
            return;
        }
        wizardFlow.startWithUrl(chatId, arguments);
    }
}
