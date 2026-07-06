package com.price.tracker.interfaces.telegram.callback;

import com.price.tracker.interfaces.telegram.admin.AdminMenuFlow;
import com.price.tracker.interfaces.telegram.menu.TrackingMenuFlow;
import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import com.price.tracker.interfaces.telegram.wizard.TrackWizardFlow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Component
@RequiredArgsConstructor
public class CallbackDispatcher {

    private static final String WIZARD_SIZE_PREFIX = "w:sz:";
    private static final String WIZARD_EVENT_PREFIX = "w:ev:";
    private static final String WIZARD_CONFIRM = "w:ok";
    private static final String WIZARD_BACK_TO_SIZES = "w:back:sz";
    private static final String WIZARD_BACK_TO_EVENTS = "w:back:ev";
    private static final String MENU_HOME = "m:home";
    private static final String MENU_ADD = "m:add";
    private static final String MENU_HELP = "m:help";
    private static final String MENU_LANG = "m:lang";
    private static final String MENU_LANG_PREFIX = "m:lang:";
    private static final String MENU_LIST = "m:list";
    private static final String MENU_ITEM_PREFIX = "m:item:";
    private static final String MENU_STOP_PREFIX = "m:stop:";
    private static final String MENU_RENAME_PREFIX = "m:ren:";
    private static final String MENU_HISTORY = "m:hist";
    private static final String MENU_RESUME_PREFIX = "m:res:";
    private static final String ADMIN_MENU = "a:menu";
    private static final String ADMIN_TEST = "a:test";
    private static final String ADMIN_CHECK = "a:check";

    private final TrackWizardFlow wizardFlow;
    private final TrackingMenuFlow menuFlow;
    private final AdminMenuFlow adminMenuFlow;
    private final TelegramReplySender replySender;

    public void dispatch(CallbackQuery callback) {
        replySender.answerCallback(callback.getId());
        long chatId = callback.getMessage().getChatId();
        int messageId = callback.getMessage().getMessageId();
        String data = callback.getData();

        if (data.startsWith(WIZARD_SIZE_PREFIX)) {
            wizardFlow.chooseSize(chatId, messageId, (int) parseNumber(data.substring(WIZARD_SIZE_PREFIX.length())));
        } else if (data.startsWith(WIZARD_EVENT_PREFIX)) {
            wizardFlow.toggleEvent(chatId, messageId, data.substring(WIZARD_EVENT_PREFIX.length()));
        } else if (WIZARD_CONFIRM.equals(data)) {
            wizardFlow.confirm(chatId, messageId);
        } else if (WIZARD_BACK_TO_SIZES.equals(data)) {
            wizardFlow.backToSizes(chatId, messageId);
        } else if (WIZARD_BACK_TO_EVENTS.equals(data)) {
            wizardFlow.backToEvents(chatId, messageId);
        } else if (MENU_HOME.equals(data)) {
            wizardFlow.abort(chatId);
            menuFlow.showHome(chatId, messageId);
        } else if (MENU_ADD.equals(data)) {
            wizardFlow.startAwaitUrl(chatId, messageId);
        } else if (MENU_HELP.equals(data)) {
            menuFlow.showHelp(chatId, messageId);
        } else if (data.startsWith(MENU_LANG_PREFIX)) {
            menuFlow.setLanguage(chatId, messageId, data.substring(MENU_LANG_PREFIX.length()));
        } else if (MENU_LANG.equals(data)) {
            menuFlow.showLanguageMenu(chatId, messageId);
        } else if (MENU_LIST.equals(data)) {
            wizardFlow.abort(chatId);
            menuFlow.showList(chatId, messageId);
        } else if (data.startsWith(MENU_ITEM_PREFIX)) {
            menuFlow.showItem(chatId, messageId, parseNumber(data.substring(MENU_ITEM_PREFIX.length())));
        } else if (data.startsWith(MENU_STOP_PREFIX)) {
            menuFlow.stopItem(chatId, messageId, parseNumber(data.substring(MENU_STOP_PREFIX.length())));
        } else if (data.startsWith(MENU_RENAME_PREFIX)) {
            menuFlow.askRename(chatId, messageId, parseNumber(data.substring(MENU_RENAME_PREFIX.length())));
        } else if (MENU_HISTORY.equals(data)) {
            wizardFlow.abort(chatId);
            menuFlow.showHistory(chatId, messageId);
        } else if (data.startsWith(MENU_RESUME_PREFIX)) {
            menuFlow.resumeItem(chatId, messageId, parseNumber(data.substring(MENU_RESUME_PREFIX.length())));
        } else if (ADMIN_MENU.equals(data)) {
            adminMenuFlow.showMenu(chatId, messageId);
        } else if (ADMIN_TEST.equals(data)) {
            adminMenuFlow.runParserTest(chatId, messageId);
        } else if (ADMIN_CHECK.equals(data)) {
            adminMenuFlow.runCheckNow(chatId, messageId);
        }
    }

    private long parseNumber(String numberText) {
        try {
            return Long.parseLong(numberText);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
