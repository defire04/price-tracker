package com.price.tracker.interfaces.telegram.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramReplySender {

    /**
     * Invisible constant-width line (U+2800 braille blanks, not trimmed by Telegram):
     * keeps menu bubbles and their inline keyboards the same width across screens
     * instead of resizing to the longest text line of each screen.
     */
    private static final String WIDTH_ANCHOR = "\n" + "⠀".repeat(32);

    private final TelegramClient telegramClient;

    public void sendText(long chatId, String text) {
        send(SendMessage.builder().chatId(chatId).text(text).build());
    }

    public Integer sendKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) {
        return send(SendMessage.builder().chatId(chatId).text(text + WIDTH_ANCHOR).replyMarkup(keyboard).build());
    }

    public void deleteMessage(long chatId, int messageId) {
        try {
            telegramClient.execute(DeleteMessage.builder().chatId(chatId).messageId(messageId).build());
        } catch (TelegramApiException e) {
            log.warn("Failed to delete message {} in chat {}: {}", messageId, chatId, e.getMessage());
        }
    }

    public void editMessage(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text + WIDTH_ANCHOR)
                .replyMarkup(keyboard)
                .build();
        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                return;
            }
            log.error("Failed to edit message {} in chat {}", messageId, chatId, e);
        }
    }

    public void answerCallback(String callbackQueryId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build());
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback {}", callbackQueryId, e);
        }
    }

    private Integer send(SendMessage message) {
        try {
            return telegramClient.execute(message).getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat {}", message.getChatId(), e);
            return null;
        }
    }
}
