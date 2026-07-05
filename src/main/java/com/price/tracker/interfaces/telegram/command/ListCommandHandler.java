package com.price.tracker.interfaces.telegram.command;

import com.price.tracker.interfaces.telegram.menu.TrackingMenuFlow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListCommandHandler implements CommandHandler {

    private final TrackingMenuFlow menuFlow;

    @Override
    public String command() {
        return "/list";
    }

    @Override
    public void handle(long chatId, String arguments) {
        menuFlow.openNewList(chatId);
    }
}
