package com.price.tracker.interfaces.telegram.command;

public interface CommandHandler {

    String command();

    void handle(long chatId, String arguments);
}
