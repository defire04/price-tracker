package com.price.tracker.interfaces.telegram.menu;

import com.price.tracker.domain.entity.PriceSnapshot;
import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.service.check.PriceCheckService;
import com.price.tracker.domain.service.tracking.TrackingDetails;
import com.price.tracker.domain.service.tracking.TrackingService;
import com.price.tracker.domain.service.user.UserService;
import com.price.tracker.exception.TrackingNotFoundException;
import com.price.tracker.interfaces.telegram.admin.AdminAccess;
import com.price.tracker.interfaces.telegram.i18n.ChatLocales;
import com.price.tracker.interfaces.telegram.view.PriceChartRenderer;
import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import com.price.tracker.interfaces.telegram.view.TrackerMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class TrackingMenuFlow {

    private record PendingRename(long itemId, int menuMessageId) {
    }

    private final TrackingService trackingService;
    private final PriceCheckService priceCheckService;
    private final UserService userService;
    private final TelegramReplySender replySender;
    private final PriceChartRenderer chartRenderer;
    private final TrackerMessages messages;
    private final ChatLocales chatLocales;
    private final AdminAccess adminAccess;
    private final ConcurrentHashMap<Long, Integer> menuMessageIdByChat = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PendingRename> pendingRenameByChat = new ConcurrentHashMap<>();
    private final ConcurrentHashMap.KeySetView<Long, Boolean> itemsBeingChecked = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Long, Integer> chartMessageIdByChat = new ConcurrentHashMap<>();

    // Telegram cannot edit a photo message into text, so when the current menu
    // is the chart photo it gets deleted and replaced with a fresh text message
    private int show(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        Integer chartMessageId = chartMessageIdByChat.get(chatId);
        if (chartMessageId != null && chartMessageId == messageId) {
            chartMessageIdByChat.remove(chatId);
            replySender.deleteMessage(chatId, messageId);
            Integer newMessageId = replySender.sendKeyboard(chatId, text, keyboard);
            rememberMenuMessage(chatId, newMessageId);
            return newMessageId != null ? newMessageId : messageId;
        }
        replySender.editMessage(chatId, messageId, text, keyboard);
        return messageId;
    }

    public void openNewHome(long chatId) {
        pendingRenameByChat.remove(chatId);
        deletePreviousMenu(chatId);
        Locale locale = chatLocales.localeOf(chatId);
        Integer messageId = replySender.sendKeyboard(chatId, messages.homeText(locale),
                messages.homeKeyboard(locale, adminAccess.isAdmin(chatId)));
        rememberMenuMessage(chatId, messageId);
    }

    public void showHome(long chatId, int messageId) {
        pendingRenameByChat.remove(chatId);
        Locale locale = chatLocales.localeOf(chatId);
        int shownMessageId = show(chatId, messageId, messages.homeText(locale),
                messages.homeKeyboard(locale, adminAccess.isAdmin(chatId)));
        rememberMenuMessage(chatId, shownMessageId);
    }

    public void showHelp(long chatId, int messageId) {
        Locale locale = chatLocales.localeOf(chatId);
        show(chatId, messageId, messages.helpText(locale), messages.backToMenuKeyboard(locale));
    }

    public void showLanguageMenu(long chatId, int messageId) {
        Locale locale = chatLocales.localeOf(chatId);
        show(chatId, messageId, messages.languagePrompt(locale), messages.languageKeyboard(locale));
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
        int shownMessageId = show(chatId, messageId,
                messages.menuListText(locale, items), messages.menuListKeyboard(locale, items));
        rememberMenuMessage(chatId, shownMessageId);
    }

    public void showHistory(long chatId, int messageId) {
        pendingRenameByChat.remove(chatId);
        Locale locale = chatLocales.localeOf(chatId);
        List<TrackedItem> items = trackingService.listInactive(chatId);
        int shownMessageId = show(chatId, messageId,
                messages.historyListText(locale, items), messages.historyListKeyboard(locale, items));
        rememberMenuMessage(chatId, shownMessageId);
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
        show(chatId, messageId,
                messages.menuItemText(locale, details), messages.menuItemKeyboard(locale, details.item()));
    }

    public void checkItemNow(long chatId, int messageId, long itemId) {
        Locale locale = chatLocales.localeOf(chatId);
        try {
            trackingService.details(chatId, itemId);
        } catch (TrackingNotFoundException e) {
            showList(chatId, messageId);
            return;
        }
        if (!itemsBeingChecked.add(itemId)) {
            return;
        }
        int shownMessageId = show(chatId, messageId, messages.itemChecking(locale), messages.backToMenuKeyboard(locale));
        Thread.ofVirtual().name("item-check-now").start(() -> {
            try {
                boolean checked = priceCheckService.checkItemNow(itemId);
                showItemAfterCheck(chatId, shownMessageId, itemId, checked);
            } finally {
                itemsBeingChecked.remove(itemId);
            }
        });
    }

    private void showItemAfterCheck(long chatId, int messageId, long itemId, boolean checked) {
        Locale locale = chatLocales.localeOf(chatId);
        TrackingDetails details;
        try {
            details = trackingService.details(chatId, itemId);
        } catch (TrackingNotFoundException e) {
            showList(chatId, messageId);
            return;
        }
        String text = messages.menuItemText(locale, details);
        if (!checked) {
            text = messages.itemCheckFailed(locale) + "\n\n" + text;
        }
        show(chatId, messageId, text, messages.menuItemKeyboard(locale, details.item()));
    }

    public void showChart(long chatId, int messageId, long itemId) {
        Locale locale = chatLocales.localeOf(chatId);
        TrackingDetails details;
        List<PriceSnapshot> history;
        try {
            details = trackingService.details(chatId, itemId);
            history = trackingService.priceHistory(chatId, itemId);
        } catch (TrackingNotFoundException e) {
            showList(chatId, messageId);
            return;
        }
        if (history.size() < 2) {
            show(chatId, messageId,
                    messages.chartNotEnough(locale) + "\n\n" + messages.menuItemText(locale, details),
                    messages.menuItemKeyboard(locale, details.item()));
            return;
        }
        // the photo becomes the menu: item buttons go right under the chart
        chartMessageIdByChat.remove(chatId);
        replySender.deleteMessage(chatId, messageId);
        Integer chartMessageId = replySender.sendPhoto(chatId,
                chartRenderer.render(history), details.item().displayTitle(),
                messages.menuItemKeyboard(locale, details.item()));
        if (chartMessageId != null) {
            chartMessageIdByChat.put(chatId, chartMessageId);
            rememberMenuMessage(chatId, chartMessageId);
        } else {
            openNewHome(chatId);
        }
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
        int shownMessageId = show(chatId, messageId,
                messages.renamePrompt(locale, details.item()), messages.renameKeyboard(locale, itemId));
        pendingRenameByChat.put(chatId, new PendingRename(itemId, shownMessageId));
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
            chartMessageIdByChat.remove(chatId, previousMenuId);
        }
    }

    private void rememberMenuMessage(long chatId, Integer messageId) {
        if (messageId != null) {
            menuMessageIdByChat.put(chatId, messageId);
        }
    }
}
