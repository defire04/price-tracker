package com.price.tracker.interfaces.telegram;

import com.price.tracker.interfaces.telegram.command.CommandHandler;
import com.price.tracker.interfaces.telegram.menu.TrackingMenuFlow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CommandDispatcher {

    private final Map<String, CommandHandler> handlersByCommand;
    private final TrackingMenuFlow menuFlow;

    public CommandDispatcher(List<CommandHandler> handlers, TrackingMenuFlow menuFlow) {
        this.handlersByCommand = handlers.stream()
                .collect(Collectors.toMap(CommandHandler::command, Function.identity()));
        this.menuFlow = menuFlow;
    }

    public void dispatch(long chatId, String messageText) {
        String[] commandAndArguments = messageText.split("\\s+", 2);
        String command = stripBotMention(commandAndArguments[0].toLowerCase());
        CommandHandler handler = handlersByCommand.get(command);
        if (handler == null) {
            menuFlow.openNewHome(chatId);
            return;
        }
        String arguments = commandAndArguments.length > 1 ? commandAndArguments[1].trim() : "";
        handler.handle(chatId, arguments);
    }

    private String stripBotMention(String command) {
        int mentionIndex = command.indexOf('@');
        return mentionIndex > 0 ? command.substring(0, mentionIndex) : command;
    }
}
