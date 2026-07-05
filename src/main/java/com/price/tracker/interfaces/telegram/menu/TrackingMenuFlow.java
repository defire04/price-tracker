package com.price.tracker.interfaces.telegram.menu;

import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.service.tracking.TrackingDetails;
import com.price.tracker.domain.service.tracking.TrackingService;
import com.price.tracker.domain.service.user.UserService;
import com.price.tracker.exception.TrackingNotFoundException;
import com.price.tracker.interfaces.telegram.i18n.ChatLocales;
import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import com.price.tracker.interfaces.telegram.view.TrackerMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class TrackingMenuFlow {

    private record PendingRename(long itemId, int menuMessageId) {
    }

    private final TrackingService trackingService;
    private final UserService userService;
    private final TelegramReplySender replySender;
    private final TrackerMessages messages;
    private final ChatLocales chatLocales;
    private final ConcurrentHashMap<Long, Integer> menuMessageIdByChat = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PendingRename> pendingRenameByChat = new ConcurrentHashMap<>();

    public void openNewHome(long chatId) {
        pendingRenameByChat.remove(chatId);
        deletePreviousMenu(chatId);
        Locale locale = chatLocales.localeOf(chatId);
        Integer messageId = replySender.sendKeyboard(chatId, messages.homeText(locale), messages.homeKeyboard(locale));
        rememberMenuMessage(chatId, messageId);
    }

    public void showHome(long chatId, int messageId) {
        pendingRenameByChat.remove(chatId);
        Locale locale = chatLocales.localeOf(chatId);
        replySender.editMessage(chatId, messageId, messages.homeText(locale), messages.homeKeyboard(locale));
        rememberMenuMessage(chatId, messageId);
    }

    public void showHelp(long chatId, int messageId) {
        Locale locale = chatLocales.localeOf(chatId);
        replySender.editMessage(chatId, messageId, messages.helpText(locale), messages.backToMenuKeyboard(locale));
    }

    public void showLanguageMenu(long chatId, int messageId) {
        Locale locale = chatLocales.localeOf(chatId);
        replySender.editMessage(chatId, messageId, messages.languagePrompt(locale), messages.languageKeyboard(locale));
    }

    public void setLanguage(long chatId, int messageId, String language) {
        userService.setPreferredLanguage(chatId, language);
        showHome(chatId, messageId);
    }

    public void openNewList(long chatId) {
        pendingRenameByChat.remove(chatId);
        deletePreviousMenu(chatId);
        Locale locale = chatLocales.localeOf(chatId);
        List<TrackedItem> items = trackingService.listActive(chatId);
        Integer messageId = replySender.sendKeyboard(chatId,
                messages.menuListText(locale, items), messages.menuListKeyboard(locale, items));
        rememberMenuMessage(chatId, messageId);
    }

    public void showList(long chatId, int messageId) {
        pendingRenameByChat.remove(chatId);
        Locale locale = chatLocales.localeOf(chatId);
        List<TrackedItem> items = trackingService.listActive(chatId);
        replySender.editMessage(chatId, messageId,
                messages.menuListText(locale, items), messages.menuListKeyboard(locale, items));
        rememberMenuMessage(chatId, messageId);
    }

    public void showHistory(long chatId, int messageId) {
        pendingRenameByChat.remove(chatId);
        Locale locale = chatLocales.localeOf(chatId);
        List<TrackedItem> items = trackingService.listInactive(chatId);
        replySender.editMessage(chatId, messageId,
                messages.historyListText(locale, items), messages.historyListKeyboard(locale, items));
        rememberMenuMessage(chatId, messageId);
    }

    public void resumeItem(long chatId, int messageId, long itemId) {
        try {
            trackingService.resume(chatId, itemId);
        } catch (TrackingNotFoundException e) {
            showHistory(chatId, messageId);
            return;
        }
        showItem(chatId, messageId, itemId);
    }

    public void showItem(long chatId, int messageId, long itemId) {
        pendingRenameByChat.remove(chatId);
        Locale locale = chatLocales.localeOf(chatId);
        TrackingDetails details;
        try {
            details = trackingService.details(chatId, itemId);
        } catch (TrackingNotFoundException e) {
            showList(chatId, messageId);
            return;
        }
        replySender.editMessage(chatId, messageId,
                messages.menuItemText(locale, details), messages.menuItemKeyboard(locale, details.item()));
    }

    public void stopItem(long chatId, int messageId, long itemId) {
        try {
            trackingService.deactivate(chatId, itemId);
        } catch (TrackingNotFoundException e) {
            // already stopped elsewhere - just refresh the list
        }
        showList(chatId, messageId);
    }

    public void askRename(long chatId, int messageId, long itemId) {
        Locale locale = chatLocales.localeOf(chatId);
        TrackingDetails details;
        try {
            details = trackingService.details(chatId, itemId);
        } catch (TrackingNotFoundException e) {
            showList(chatId, messageId);
            return;
        }
        pendingRenameByChat.put(chatId, new PendingRename(itemId, messageId));
        replySender.editMessage(chatId, messageId,
                messages.renamePrompt(locale, details.item()), messages.renameKeyboard(locale, itemId));
    }

    public boolean handleRenameText(long chatId, int userMessageId, String text) {
        PendingRename pending = pendingRenameByChat.remove(chatId);
        if (pending == null) {
            return false;
        }
        replySender.deleteMessage(chatId, userMessageId);
        try {
            trackingService.rename(chatId, pending.itemId(), text);
        } catch (TrackingNotFoundException e) {
            showList(chatId, pending.menuMessageId());
            return true;
        }
        showItem(chatId, pending.menuMessageId(), pending.itemId());
        return true;
    }

    private void deletePreviousMenu(long chatId) {
        Integer previousMenuId = menuMessageIdByChat.remove(chatId);
        if (previousMenuId != null) {
            replySender.deleteMessage(chatId, previousMenuId);
        }
    }

    private void rememberMenuMessage(long chatId, Integer messageId) {
        if (messageId != null) {
            menuMessageIdByChat.put(chatId, messageId);
        }
    }
}
