package com.price.tracker.interfaces.telegram.wizard;

import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.enums.TrackedEventType;
import com.price.tracker.domain.service.tracking.NewTrackingCommand;
import com.price.tracker.domain.service.tracking.ProductPreview;
import com.price.tracker.domain.service.tracking.TrackingService;
import com.price.tracker.exception.ProductParsingException;
import com.price.tracker.exception.UnsupportedSiteException;
import com.price.tracker.interfaces.telegram.view.TelegramReplySender;
import com.price.tracker.interfaces.telegram.view.TrackerMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    private static final String ONLY_INTERTOP_TEXT = "⚠️ Поки що підтримується лише intertop.ua";
    private static final String PARSE_FAILED_TEXT = "⚠️ Не вдалося прочитати сторінку товару. Перевір посилання.";

    private final TrackWizardStore wizardStore;
    private final TrackingService trackingService;
    private final TelegramReplySender replySender;

    public void startAwaitUrl(long chatId, int messageId) {
        TrackWizardState state = new TrackWizardState();
        state.setMenuMessageId(messageId);
        wizardStore.put(chatId, state);
        replySender.editMessage(chatId, messageId, TrackerMessages.urlPrompt(), TrackerMessages.backToMenuKeyboard());
    }

    public void startAwaitUrlNewMessage(long chatId) {
        TrackWizardState state = new TrackWizardState();
        wizardStore.put(chatId, state);
        Integer messageId = replySender.sendKeyboard(chatId, TrackerMessages.urlPrompt(), TrackerMessages.backToMenuKeyboard());
        if (messageId != null) {
            state.setMenuMessageId(messageId);
        }
    }

    public void startWithUrl(long chatId, String url) {
        ProductPreview preview;
        try {
            preview = trackingService.preview(url);
        } catch (UnsupportedSiteException e) {
            replySender.sendKeyboard(chatId, ONLY_INTERTOP_TEXT, TrackerMessages.backToMenuKeyboard());
            return;
        } catch (ProductParsingException e) {
            log.warn("Product preview failed for {}", url, e);
            replySender.sendKeyboard(chatId, PARSE_FAILED_TEXT, TrackerMessages.backToMenuKeyboard());
            return;
        }
        TrackWizardState state = new TrackWizardState();
        state.initProduct(url, preview.site(), preview.product());
        wizardStore.put(chatId, state);
        Integer messageId = replySender.sendKeyboard(chatId,
                TrackerMessages.productPreview(preview.product()),
                TrackerMessages.sizeKeyboard(preview.product().sizes()));
        if (messageId != null) {
            state.setMenuMessageId(messageId);
        }
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
        if (state.getSelectedEvents().isEmpty()) {
            replySender.editMessage(chatId, messageId,
                    TrackerMessages.eventsPrompt(state.getSizeLabel()) + "\n\n⚠️ Обери хоча б одну подію",
                    TrackerMessages.eventsKeyboard(state.getSelectedEvents()));
            return;
        }
        if (state.getSelectedEvents().contains(TrackedEventType.DISCOUNT_PERCENT)) {
            state.setStep(TrackWizardStep.AWAIT_THRESHOLD);
            replySender.editMessage(chatId, messageId, TrackerMessages.thresholdPrompt(), TrackerMessages.thresholdKeyboard());
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
        ProductPreview preview;
        try {
            preview = trackingService.preview(url);
        } catch (UnsupportedSiteException e) {
            replySender.editMessage(chatId, state.getMenuMessageId(),
                    ONLY_INTERTOP_TEXT + "\n\n" + TrackerMessages.urlPrompt(), TrackerMessages.backToMenuKeyboard());
            return;
        } catch (ProductParsingException e) {
            log.warn("Product preview failed for {}", url, e);
            replySender.editMessage(chatId, state.getMenuMessageId(),
                    PARSE_FAILED_TEXT + "\n\n" + TrackerMessages.urlPrompt(), TrackerMessages.backToMenuKeyboard());
            return;
        }
        state.initProduct(url, preview.site(), preview.product());
        showSizeStep(chatId, state.getMenuMessageId(), state);
    }

    private void handleThresholdInput(long chatId, TrackWizardState state, String text) {
        Integer threshold = parseThreshold(text);
        if (threshold == null) {
            replySender.editMessage(chatId, state.getMenuMessageId(),
                    TrackerMessages.thresholdPrompt() + "\n\n⚠️ Потрібне ціле число від " + MIN_THRESHOLD_PERCENT
                            + " до " + MAX_THRESHOLD_PERCENT,
                    TrackerMessages.thresholdKeyboard());
            return;
        }
        finish(chatId, state.getMenuMessageId(), state, BigDecimal.valueOf(threshold));
    }

    private void finish(long chatId, int messageId, TrackWizardState state, BigDecimal discountThreshold) {
        NewTrackingCommand command = new NewTrackingCommand(
                state.getUrl(), state.getSite(), state.getProduct(),
                state.getSizeLabel(), state.getSelectedEvents(), discountThreshold);
        TrackedItem item = trackingService.createTracking(chatId, command);
        wizardStore.remove(chatId);
        replySender.editMessage(chatId, messageId,
                TrackerMessages.trackingCreated(item), TrackerMessages.trackingCreatedKeyboard());
    }

    private void showSizeStep(long chatId, int messageId, TrackWizardState state) {
        state.setMenuMessageId(messageId);
        replySender.editMessage(chatId, messageId,
                TrackerMessages.productPreview(state.getProduct()),
                TrackerMessages.sizeKeyboard(state.getProduct().sizes()));
    }

    private void showEventsStep(long chatId, int messageId, TrackWizardState state) {
        state.setMenuMessageId(messageId);
        replySender.editMessage(chatId, messageId,
                TrackerMessages.eventsPrompt(state.getSizeLabel()),
                TrackerMessages.eventsKeyboard(state.getSelectedEvents()));
    }

    private Optional<TrackWizardState> findOrExpire(long chatId, int messageId) {
        Optional<TrackWizardState> state = wizardStore.find(chatId);
        if (state.isEmpty()) {
            replySender.editMessage(chatId, messageId,
                    "Сесія застаріла. Почни заново.", TrackerMessages.backToMenuKeyboard());
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
