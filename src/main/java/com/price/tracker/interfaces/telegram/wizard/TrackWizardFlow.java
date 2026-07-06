package com.price.tracker.interfaces.telegram.wizard;

import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.enums.TrackedEventType;
import com.price.tracker.domain.service.notification.AdminNotifier;
import com.price.tracker.domain.service.tracking.NewTrackingCommand;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.service.tracking.ProductPreview;
import com.price.tracker.domain.service.tracking.TrackingService;
import com.price.tracker.exception.ProductParsingException;
import com.price.tracker.exception.UnsupportedSiteException;
import com.price.tracker.interfaces.telegram.i18n.ChatLocales;
import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import com.price.tracker.interfaces.telegram.view.TrackerMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrackWizardFlow {

    private static final Map<String, TrackedEventType> EVENTS_BY_CODE = Map.of(
            "R", TrackedEventType.RESTOCK,
            "PD", TrackedEventType.PRICE_DROP,
            "PI", TrackedEventType.PRICE_INCREASE,
            "DP", TrackedEventType.DISCOUNT_PERCENT);

    private static final int MIN_THRESHOLD_PERCENT = 1;
    private static final int MAX_THRESHOLD_PERCENT = 99;

    private final TrackWizardStore wizardStore;
    private final TrackingService trackingService;
    private final TelegramReplySender replySender;
    private final TrackerMessages messages;
    private final ChatLocales chatLocales;
    private final AdminNotifier adminNotifier;

    public void startAwaitUrl(long chatId, int messageId) {
        Locale locale = chatLocales.localeOf(chatId);
        TrackWizardState state = new TrackWizardState();
        state.setMenuMessageId(messageId);
        wizardStore.put(chatId, state);
        replySender.editMessage(chatId, messageId, messages.urlPrompt(locale), messages.backToMenuKeyboard(locale));
    }

    public void startAwaitUrlNewMessage(long chatId) {
        Locale locale = chatLocales.localeOf(chatId);
        TrackWizardState state = new TrackWizardState();
        wizardStore.put(chatId, state);
        Integer messageId = replySender.sendKeyboard(chatId, messages.urlPrompt(locale), messages.backToMenuKeyboard(locale));
        if (messageId != null) {
            state.setMenuMessageId(messageId);
        }
    }

    public void startWithUrl(long chatId, String url) {
        Locale locale = chatLocales.localeOf(chatId);
        ProductPreview preview;
        try {
            preview = trackingService.preview(url);
        } catch (UnsupportedSiteException e) {
            replySender.sendKeyboard(chatId, messages.urlUnsupported(locale), messages.backToMenuKeyboard(locale));
            return;
        } catch (ProductParsingException e) {
            log.warn("Product preview failed for {}", url, e);
            adminNotifier.parserFailed(url, e.toString());
            replySender.sendKeyboard(chatId, messages.urlParseFailed(locale), messages.backToMenuKeyboard(locale));
            return;
        }
        TrackWizardState state = new TrackWizardState();
        state.initProduct(url, preview.site(), preview.product());
        wizardStore.put(chatId, state);
        Integer messageId = replySender.sendKeyboard(chatId,
                productStepText(locale, state), productStepKeyboard(locale, state));
        if (messageId != null) {
            state.setMenuMessageId(messageId);
        }
    }

    public void chooseVariant(long chatId, int messageId, int groupIndex, int optionIndex) {
        Optional<TrackWizardState> found = findOrExpire(chatId, messageId);
        if (found.isEmpty()) {
            return;
        }
        TrackWizardState state = found.get();
        if (state.getStep() != TrackWizardStep.CHOOSE_VARIANT || state.getProduct() == null
                || groupIndex < 0 || groupIndex >= state.getProduct().variants().size()) {
            return;
        }
        List<ProductSnapshot.VariantOption> options = state.getProduct().variants().get(groupIndex).options();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return;
        }
        ProductSnapshot.VariantOption option = options.get(optionIndex);
        if (option.selected() || option.url() == null) {
            return;
        }
        ProductPreview preview;
        try {
            preview = trackingService.preview(option.url());
        } catch (ProductParsingException | UnsupportedSiteException e) {
            log.warn("Variant preview failed for {}", option.url(), e);
            adminNotifier.parserFailed(option.url(), e.toString());
            return;
        }
        state.initProduct(option.url(), preview.site(), preview.product());
        showProductStep(chatId, messageId, state);
    }

    public void variantsDone(long chatId, int messageId) {
        Optional<TrackWizardState> found = findOrExpire(chatId, messageId);
        if (found.isEmpty() || found.get().getProduct() == null) {
            return;
        }
        TrackWizardState state = found.get();
        if (state.getStep() != TrackWizardStep.CHOOSE_VARIANT) {
            return;
        }
        state.setStep(TrackWizardStep.CHOOSE_SIZE);
        showSizeStep(chatId, messageId, state);
    }

    public boolean handleText(long chatId, int userMessageId, String text) {
        Optional<TrackWizardState> found = wizardStore.find(chatId);
        if (found.isEmpty()) {
            return false;
        }
        TrackWizardState state = found.get();
        switch (state.getStep()) {
            case AWAIT_URL -> {
                replySender.deleteMessage(chatId, userMessageId);
                handleUrlInput(chatId, state, text.trim());
                return true;
            }
            case AWAIT_THRESHOLD -> {
                replySender.deleteMessage(chatId, userMessageId);
                handleThresholdInput(chatId, state, text.trim());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public void chooseSize(long chatId, int messageId, int sizeIndex) {
        Optional<TrackWizardState> found = findOrExpire(chatId, messageId);
        if (found.isEmpty()) {
            return;
        }
        TrackWizardState state = found.get();
        if (state.getProduct() == null || sizeIndex < 0 || sizeIndex >= state.getProduct().sizes().size()) {
            return;
        }
        state.setSizeLabel(state.getProduct().sizes().get(sizeIndex).label());
        state.setStep(TrackWizardStep.CHOOSE_EVENTS);
        showEventsStep(chatId, messageId, state);
    }

    public void changeSizePage(long chatId, int messageId, int page) {
        Optional<TrackWizardState> found = findOrExpire(chatId, messageId);
        if (found.isEmpty()) {
            return;
        }
        TrackWizardState state = found.get();
        if (state.getProduct() == null || state.getStep() != TrackWizardStep.CHOOSE_SIZE) {
            return;
        }
        Locale locale = chatLocales.localeOf(chatId);
        replySender.editMessage(chatId, messageId,
                messages.productPreview(locale, state.getProduct()),
                messages.sizeKeyboard(locale, state.getProduct().sizes(), page));
    }

    public void toggleEvent(long chatId, int messageId, String eventCode) {
        Optional<TrackWizardState> found = findOrExpire(chatId, messageId);
        if (found.isEmpty()) {
            return;
        }
        TrackWizardState state = found.get();
        TrackedEventType eventType = EVENTS_BY_CODE.get(eventCode);
        if (eventType == null || state.getStep() != TrackWizardStep.CHOOSE_EVENTS) {
            return;
        }
        state.toggleEvent(eventType);
        showEventsStep(chatId, messageId, state);
    }

    public void confirm(long chatId, int messageId) {
        Optional<TrackWizardState> found = findOrExpire(chatId, messageId);
        if (found.isEmpty()) {
            return;
        }
        TrackWizardState state = found.get();
        Locale locale = chatLocales.localeOf(chatId);
        if (state.getSelectedEvents().isEmpty()) {
            replySender.editMessage(chatId, messageId,
                    messages.eventsPromptWithWarning(locale, state.getSizeLabel()),
                    messages.eventsKeyboard(locale, state.getSelectedEvents()));
            return;
        }
        if (state.getSelectedEvents().contains(TrackedEventType.DISCOUNT_PERCENT)) {
            state.setStep(TrackWizardStep.AWAIT_THRESHOLD);
            replySender.editMessage(chatId, messageId, messages.thresholdPrompt(locale), messages.thresholdKeyboard(locale));
            return;
        }
        finish(chatId, messageId, state, null);
    }

    public void backToSizes(long chatId, int messageId) {
        Optional<TrackWizardState> found = findOrExpire(chatId, messageId);
        if (found.isEmpty() || found.get().getProduct() == null) {
            return;
        }
        TrackWizardState state = found.get();
        state.setSizeLabel(null);
        state.setStep(TrackWizardStep.CHOOSE_SIZE);
        showSizeStep(chatId, messageId, state);
    }

    public void backToEvents(long chatId, int messageId) {
        Optional<TrackWizardState> found = findOrExpire(chatId, messageId);
        if (found.isEmpty() || found.get().getSizeLabel() == null) {
            return;
        }
        TrackWizardState state = found.get();
        state.setStep(TrackWizardStep.CHOOSE_EVENTS);
        showEventsStep(chatId, messageId, state);
    }

    public void abort(long chatId) {
        wizardStore.remove(chatId);
    }

    private void handleUrlInput(long chatId, TrackWizardState state, String url) {
        Locale locale = chatLocales.localeOf(chatId);
        ProductPreview preview;
        try {
            preview = trackingService.preview(url);
        } catch (UnsupportedSiteException e) {
            replySender.editMessage(chatId, state.getMenuMessageId(),
                    messages.urlUnsupported(locale) + "\n\n" + messages.urlPrompt(locale),
                    messages.backToMenuKeyboard(locale));
            return;
        } catch (ProductParsingException e) {
            log.warn("Product preview failed for {}", url, e);
            replySender.editMessage(chatId, state.getMenuMessageId(),
                    messages.urlParseFailed(locale) + "\n\n" + messages.urlPrompt(locale),
                    messages.backToMenuKeyboard(locale));
            return;
        }
        state.initProduct(url, preview.site(), preview.product());
        showProductStep(chatId, state.getMenuMessageId(), state);
    }

    private void handleThresholdInput(long chatId, TrackWizardState state, String text) {
        Integer threshold = parseThreshold(text);
        if (threshold == null) {
            Locale locale = chatLocales.localeOf(chatId);
            replySender.editMessage(chatId, state.getMenuMessageId(),
                    messages.thresholdInvalid(locale, MIN_THRESHOLD_PERCENT, MAX_THRESHOLD_PERCENT),
                    messages.thresholdKeyboard(locale));
            return;
        }
        finish(chatId, state.getMenuMessageId(), state, BigDecimal.valueOf(threshold));
    }

    private void finish(long chatId, int messageId, TrackWizardState state, BigDecimal discountThreshold) {
        Locale locale = chatLocales.localeOf(chatId);
        NewTrackingCommand command = new NewTrackingCommand(
                state.getUrl(), state.getSite(), state.getProduct(),
                state.getSizeLabel(), state.getSelectedEvents(), discountThreshold);
        TrackedItem item = trackingService.createTracking(chatId, command);
        wizardStore.remove(chatId);
        replySender.editMessage(chatId, messageId,
                messages.trackingCreated(locale, item), messages.trackingCreatedKeyboard(locale));
    }

    private void showProductStep(long chatId, int messageId, TrackWizardState state) {
        if (state.getStep() == TrackWizardStep.CHOOSE_VARIANT) {
            Locale locale = chatLocales.localeOf(chatId);
            state.setMenuMessageId(messageId);
            replySender.editMessage(chatId, messageId,
                    messages.variantPrompt(locale, state.getProduct()),
                    messages.variantKeyboard(locale, state.getProduct().variants()));
            return;
        }
        showSizeStep(chatId, messageId, state);
    }

    private String productStepText(Locale locale, TrackWizardState state) {
        return state.getStep() == TrackWizardStep.CHOOSE_VARIANT
                ? messages.variantPrompt(locale, state.getProduct())
                : messages.productPreview(locale, state.getProduct());
    }

    private InlineKeyboardMarkup productStepKeyboard(Locale locale, TrackWizardState state) {
        return state.getStep() == TrackWizardStep.CHOOSE_VARIANT
                ? messages.variantKeyboard(locale, state.getProduct().variants())
                : messages.sizeKeyboard(locale, state.getProduct().sizes(), 0);
    }

    private void showSizeStep(long chatId, int messageId, TrackWizardState state) {
        Locale locale = chatLocales.localeOf(chatId);
        state.setMenuMessageId(messageId);
        replySender.editMessage(chatId, messageId,
                messages.productPreview(locale, state.getProduct()),
                messages.sizeKeyboard(locale, state.getProduct().sizes(), 0));
    }

    private void showEventsStep(long chatId, int messageId, TrackWizardState state) {
        Locale locale = chatLocales.localeOf(chatId);
        state.setMenuMessageId(messageId);
        replySender.editMessage(chatId, messageId,
                messages.eventsPrompt(locale, state.getSizeLabel()),
                messages.eventsKeyboard(locale, state.getSelectedEvents()));
    }

    private Optional<TrackWizardState> findOrExpire(long chatId, int messageId) {
        Optional<TrackWizardState> state = wizardStore.find(chatId);
        if (state.isEmpty()) {
            Locale locale = chatLocales.localeOf(chatId);
            replySender.editMessage(chatId, messageId, messages.sessionExpired(locale), messages.backToMenuKeyboard(locale));
        }
        return state;
    }

    private Integer parseThreshold(String text) {
        try {
            int threshold = Integer.parseInt(text.replace("%", "").trim());
            return threshold >= MIN_THRESHOLD_PERCENT && threshold <= MAX_THRESHOLD_PERCENT ? threshold : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
