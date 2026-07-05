package com.price.tracker.interfaces.telegram;

import com.price.tracker.domain.model.TelegramProfile;
import com.price.tracker.domain.service.user.UserService;
import com.price.tracker.interfaces.telegram.callback.CallbackDispatcher;
import com.price.tracker.interfaces.telegram.menu.TrackingMenuFlow;
import com.price.tracker.interfaces.telegram.wizard.TrackWizardFlow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramUpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    private final CommandDispatcher commandDispatcher;
    private final CallbackDispatcher callbackDispatcher;
    private final TrackWizardFlow wizardFlow;
    private final TrackingMenuFlow menuFlow;
    private final UserService userService;

    @Override
    public void consume(Update update) {
        try {
            route(update);
        } catch (Exception e) {
            log.error("Failed to handle update {}", update.getUpdateId(), e);
        }
    }

    private void route(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
            return;
        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update.getMessage());
        }
    }

    private void handleCallback(CallbackQuery callback) {
        upsertUser(callback.getMessage().getChatId(), callback.getFrom());
        callbackDispatcher.dispatch(callback);
    }

    private void handleTextMessage(Message message) {
        long chatId = message.getChatId();
        upsertUser(chatId, message.getFrom());
        String text = message.getText().trim();
        if (text.startsWith("/")) {
            commandDispatcher.dispatch(chatId, text);
            return;
        }
        boolean handled = wizardFlow.handleText(chatId, message.getMessageId(), text)
                || menuFlow.handleRenameText(chatId, message.getMessageId(), text);
        if (!handled) {
            menuFlow.openNewHome(chatId);
        }
    }

    private void upsertUser(long chatId, User telegramUser) {
        if (telegramUser == null) {
            return;
        }
        userService.registerOrUpdate(chatId, new TelegramProfile(
                telegramUser.getUserName(),
                telegramUser.getFirstName(),
                telegramUser.getLastName(),
                telegramUser.getLanguageCode()));
    }
}
