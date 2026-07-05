package com.price.tracker.interfaces.telegram.view;

import com.price.tracker.domain.entity.PriceSnapshot;
import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.entity.TrackedItemEvent;
import com.price.tracker.domain.enums.TrackedEventType;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.service.tracking.TrackingDetails;
import com.price.tracker.util.KyivTime;
import com.price.tracker.util.PriceFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TrackerMessages {

    private static final int SIZE_BUTTONS_PER_ROW = 4;
    private static final int ITEM_BUTTON_TITLE_LENGTH = 32;

    private final MessageSource messageSource;

    public String homeText(Locale locale) {
        return text(locale, "home.text");
    }

    public InlineKeyboardMarkup homeKeyboard(Locale locale) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button(text(locale, "btn.add"), "m:add")),
                new InlineKeyboardRow(button(text(locale, "btn.list"), "m:list"), button(text(locale, "btn.history"), "m:hist")),
                new InlineKeyboardRow(button(text(locale, "btn.help"), "m:help"), button(text(locale, "btn.lang"), "m:lang"))));
    }

    public String helpText(Locale locale) {
        return text(locale, "help.text");
    }

    public InlineKeyboardMarkup backToMenuKeyboard(Locale locale) {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button(text(locale, "btn.backToMenu"), "m:home"))));
    }

    public String languagePrompt(Locale locale) {
        return text(locale, "lang.prompt");
    }

    public InlineKeyboardMarkup languageKeyboard(Locale locale) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button(text(locale, "lang.uk"), "m:lang:uk")),
                new InlineKeyboardRow(button(text(locale, "lang.ru"), "m:lang:ru")),
                new InlineKeyboardRow(button(text(locale, "lang.en"), "m:lang:en")),
                new InlineKeyboardRow(button(text(locale, "btn.back"), "m:home"))));
    }

    public String urlPrompt(Locale locale) {
        return text(locale, "url.prompt");
    }

    public String urlUnsupported(Locale locale) {
        return text(locale, "url.unsupported");
    }

    public String urlParseFailed(Locale locale) {
        return text(locale, "url.parseFailed");
    }

    public String productPreview(Locale locale, ProductSnapshot product) {
        StringBuilder result = new StringBuilder(product.title()).append("\n");
        result.append(text(locale, "price.label", PriceFormat.format(product.price())));
        if (product.regularPrice() != null) {
            int discount = PriceFormat.percentOf(product.regularPrice().subtract(product.price()), product.regularPrice());
            result.append(" ").append(text(locale, "price.was", PriceFormat.format(product.regularPrice()), discount));
        }
        result.append("\n\n").append(text(locale, "choose.size"));
        return result.toString();
    }

    public InlineKeyboardMarkup sizeKeyboard(Locale locale, List<SizeOffer> sizes) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow currentRow = new InlineKeyboardRow();
        for (int sizeIndex = 0; sizeIndex < sizes.size(); sizeIndex++) {
            SizeOffer size = sizes.get(sizeIndex);
            String label = size.inStock() ? size.label() : size.label() + " ✗";
            currentRow.add(button(label, "w:sz:" + sizeIndex));
            if (currentRow.size() == SIZE_BUTTONS_PER_ROW) {
                rows.add(currentRow);
                currentRow = new InlineKeyboardRow();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }
        rows.add(new InlineKeyboardRow(button(text(locale, "btn.backToMenu"), "m:home")));
        return new InlineKeyboardMarkup(rows);
    }

    public String eventsPrompt(Locale locale, String sizeLabel) {
        return text(locale, "events.prompt", displaySize(locale, sizeLabel));
    }

    public String eventsPromptWithWarning(Locale locale, String sizeLabel) {
        return eventsPrompt(locale, sizeLabel) + "\n\n" + text(locale, "events.atLeastOne");
    }

    public InlineKeyboardMarkup eventsKeyboard(Locale locale, Set<TrackedEventType> selectedEvents) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(eventButton(locale, TrackedEventType.RESTOCK, "R", selectedEvents)),
                new InlineKeyboardRow(eventButton(locale, TrackedEventType.PRICE_DROP, "PD", selectedEvents)),
                new InlineKeyboardRow(eventButton(locale, TrackedEventType.PRICE_INCREASE, "PI", selectedEvents)),
                new InlineKeyboardRow(eventButton(locale, TrackedEventType.DISCOUNT_PERCENT, "DP", selectedEvents)),
                new InlineKeyboardRow(button(text(locale, "btn.back"), "w:back:sz"), button(text(locale, "btn.done"), "w:ok"))));
    }

    public String thresholdPrompt(Locale locale) {
        return text(locale, "threshold.prompt");
    }

    public String thresholdInvalid(Locale locale, int min, int max) {
        return thresholdPrompt(locale) + "\n\n" + text(locale, "threshold.invalid", min, max);
    }

    public InlineKeyboardMarkup thresholdKeyboard(Locale locale) {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button(text(locale, "btn.back"), "w:back:ev"))));
    }

    public String sessionExpired(Locale locale) {
        return text(locale, "session.expired");
    }

    public String trackingCreated(Locale locale, TrackedItem item) {
        return text(locale, "created.title") + "\n"
                + item.displayTitle() + sizeSuffix(locale, item.getSizeLabel()) + "\n"
                + text(locale, "events.label", formatEvents(locale, item));
    }

    public InlineKeyboardMarkup trackingCreatedKeyboard(Locale locale) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button(text(locale, "btn.addMore"), "m:add"), button(text(locale, "btn.list"), "m:list")),
                new InlineKeyboardRow(button(text(locale, "btn.menu"), "m:home"))));
    }

    public String menuListText(Locale locale, List<TrackedItem> items) {
        if (items.isEmpty()) {
            return text(locale, "list.empty");
        }
        return text(locale, "list.header", items.size());
    }

    public InlineKeyboardMarkup menuListKeyboard(Locale locale, List<TrackedItem> items) {
        List<InlineKeyboardRow> rows = items.stream()
                .map(item -> new InlineKeyboardRow(button(menuItemButtonLabel(item), "m:item:" + item.getId())))
                .collect(Collectors.toCollection(ArrayList::new));
        rows.add(new InlineKeyboardRow(button(text(locale, "btn.addShort"), "m:add"), button(text(locale, "btn.refresh"), "m:list")));
        rows.add(new InlineKeyboardRow(button(text(locale, "btn.menu"), "m:home")));
        return new InlineKeyboardMarkup(rows);
    }

    public String historyListText(Locale locale, List<TrackedItem> items) {
        if (items.isEmpty()) {
            return text(locale, "history.empty");
        }
        return text(locale, "history.header", items.size());
    }

    public InlineKeyboardMarkup historyListKeyboard(Locale locale, List<TrackedItem> items) {
        List<InlineKeyboardRow> rows = items.stream()
                .map(item -> new InlineKeyboardRow(button(menuItemButtonLabel(item), "m:item:" + item.getId())))
                .collect(Collectors.toCollection(ArrayList::new));
        rows.add(new InlineKeyboardRow(button(text(locale, "btn.menu"), "m:home")));
        return new InlineKeyboardMarkup(rows);
    }

    public String menuItemText(Locale locale, TrackingDetails details) {
        TrackedItem item = details.item();
        PriceSnapshot lastSnapshot = details.lastSnapshot();
        StringBuilder result = new StringBuilder(item.displayTitle())
                .append(sizeSuffix(locale, item.getSizeLabel())).append("\n");
        if (!item.isActive()) {
            result.append(text(locale, "item.stopped")).append("\n");
        }
        if (lastSnapshot != null) {
            result.append(text(locale, "price.label", PriceFormat.format(lastSnapshot.getPrice())));
            if (lastSnapshot.getRegularPrice() != null) {
                result.append(" ").append(text(locale, "price.wasShort", PriceFormat.format(lastSnapshot.getRegularPrice())));
            }
            String stock = text(locale, lastSnapshot.isInStock() ? "item.stock.in" : "item.stock.out");
            result.append("\n").append(text(locale, "item.stock", stock)).append("\n");
            result.append(text(locale, "item.checked", KyivTime.format(lastSnapshot.getCheckedAt()))).append("\n");
        }
        result.append(text(locale, "events.label", formatEvents(locale, item))).append("\n");
        result.append(item.getUrl());
        return result.toString();
    }

    public InlineKeyboardMarkup menuItemKeyboard(Locale locale, TrackedItem item) {
        if (!item.isActive()) {
            return new InlineKeyboardMarkup(List.of(
                    new InlineKeyboardRow(button(text(locale, "btn.resume"), "m:res:" + item.getId())),
                    new InlineKeyboardRow(button(text(locale, "btn.back"), "m:hist"))));
        }
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button(text(locale, "btn.rename"), "m:ren:" + item.getId()), button(text(locale, "btn.stop"), "m:stop:" + item.getId())),
                new InlineKeyboardRow(button(text(locale, "btn.back"), "m:list"))));
    }

    public String renamePrompt(Locale locale, TrackedItem item) {
        return text(locale, "rename.prompt", item.displayTitle());
    }

    public InlineKeyboardMarkup renameKeyboard(Locale locale, long itemId) {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button(text(locale, "btn.back"), "m:item:" + itemId))));
    }

    public String stopUsage(Locale locale) {
        return text(locale, "stop.usage");
    }

    public String stopDone(Locale locale) {
        return text(locale, "stop.done");
    }

    public String stopNotFound(Locale locale) {
        return text(locale, "stop.notFound");
    }

    private String text(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    private String menuItemButtonLabel(TrackedItem item) {
        String title = item.displayTitle();
        if (title.length() > ITEM_BUTTON_TITLE_LENGTH) {
            title = title.substring(0, ITEM_BUTTON_TITLE_LENGTH) + "…";
        }
        String size = SizeOffer.ONE_SIZE_LABEL.equals(item.getSizeLabel()) ? "" : " · " + item.getSizeLabel();
        return title + size;
    }

    private String formatEvents(Locale locale, TrackedItem item) {
        return item.getEvents().stream()
                .filter(event -> event.isActive() || !item.isActive())
                .map(event -> eventDisplay(locale, event, item))
                .collect(Collectors.joining(", "));
    }

    private String eventDisplay(Locale locale, TrackedItemEvent event, TrackedItem item) {
        if (event.getEventType() == TrackedEventType.DISCOUNT_PERCENT && item.getDiscountThreshold() != null) {
            return text(locale, "event.discountThreshold", item.getDiscountThreshold().stripTrailingZeros().toPlainString());
        }
        return text(locale, "event." + event.getEventType().name());
    }

    private String sizeSuffix(Locale locale, String sizeLabel) {
        return SizeOffer.ONE_SIZE_LABEL.equals(sizeLabel) ? "" : " " + text(locale, "size.suffix", sizeLabel);
    }

    private String displaySize(Locale locale, String sizeLabel) {
        return SizeOffer.ONE_SIZE_LABEL.equals(sizeLabel) ? text(locale, "size.one") : sizeLabel;
    }

    private InlineKeyboardButton eventButton(Locale locale,
                                             TrackedEventType eventType,
                                             String code,
                                             Set<TrackedEventType> selectedEvents) {
        String prefix = selectedEvents.contains(eventType) ? "✅ " : "";
        return button(prefix + text(locale, "event." + eventType.name()), "w:ev:" + code);
    }

    private InlineKeyboardButton button(String buttonText, String callbackData) {
        return InlineKeyboardButton.builder().text(buttonText).callbackData(callbackData).build();
    }
}
