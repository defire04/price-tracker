package com.price.tracker.interfaces.telegram.menu;

import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.service.tracking.TrackingDetails;
import com.price.tracker.domain.service.tracking.TrackingService;
import com.price.tracker.exception.TrackingNotFoundException;
import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import com.price.tracker.interfaces.telegram.view.TrackerMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class TrackingMenuFlow {

    private record PendingRename(long itemId, int menuMessageId) {
    }

    private final TrackingService trackingService;
    private final TelegramReplySender replySender;
    private final ConcurrentHashMap<Long, Integer> menuMessageIdByChat = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PendingRename> pendingRenameByChat = new ConcurrentHashMap<>();

    public void openNewHome(long chatId) {
        pendingRenameByChat.remove(chatId);
        deletePreviousMenu(chatId);
        Integer messageId = replySender.sendKeyboard(chatId, TrackerMessages.homeText(), TrackerMessages.homeKeyboard());
        rememberMenuMessage(chatId, messageId);
    }

    public void showHome(long chatId, int messageId) {
        pendingRenameByChat.remove(chatId);
        replySender.editMessage(chatId, messageId, TrackerMessages.homeText(), TrackerMessages.homeKeyboard());
        rememberMenuMessage(chatId, messageId);
    }

    public void showHelp(long chatId, int messageId) {
        replySender.editMessage(chatId, messageId, TrackerMessages.helpText(), TrackerMessages.backToMenuKeyboard());
    }

    public void openNewList(long chatId) {
        pendingRenameByChat.remove(chatId);
        deletePreviousMenu(chatId);
        List<TrackedItem> items = trackingService.listActive(chatId);
        Integer messageId = replySender.sendKeyboard(chatId,
                TrackerMessages.menuListText(items), TrackerMessages.menuListKeyboard(items));
        rememberMenuMessage(chatId, messageId);
    }

    public void showList(long chatId, int messageId) {
        pendingRenameByChat.remove(chatId);
        List<TrackedItem> items = trackingService.listActive(chatId);
        replySender.editMessage(chatId, messageId,
                TrackerMessages.menuListText(items), TrackerMessages.menuListKeyboard(items));
        rememberMenuMessage(chatId, messageId);
    }

    public void showItem(long chatId, int messageId, long itemId) {
        pendingRenameByChat.remove(chatId);
        TrackingDetails details;
        try {
            details = trackingService.details(chatId, itemId);
        } catch (TrackingNotFoundException e) {
            showList(chatId, messageId);
            return;
        }
        replySender.editMessage(chatId, messageId,
                TrackerMessages.menuItemText(details), TrackerMessages.menuItemKeyboard(itemId));
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
        TrackingDetails details;
        try {
            details = trackingService.details(chatId, itemId);
        } catch (TrackingNotFoundException e) {
            showList(chatId, messageId);
            return;
        }
        pendingRenameByChat.put(chatId, new PendingRename(itemId, messageId));
        replySender.editMessage(chatId, messageId,
                TrackerMessages.renamePrompt(details.item()), TrackerMessages.renameKeyboard(itemId));
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
