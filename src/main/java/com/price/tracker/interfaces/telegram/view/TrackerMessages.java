package com.price.tracker.interfaces.telegram.view;

import com.price.tracker.domain.entity.PriceSnapshot;
import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.entity.TrackedItemEvent;
import com.price.tracker.domain.enums.Site;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TrackerMessages {

    private static final int SIZE_BUTTONS_PER_ROW = 4;
    private static final int ITEM_BUTTON_TITLE_LENGTH = 32;
    // Aggregators return dozens of shop offers - page them instead of one huge keyboard
    private static final int OFFERS_PER_PAGE = 5;
    private static final int VARIANT_BUTTONS_PER_ROW = 3;

    private final MessageSource messageSource;

    public String homeText(Locale locale) {
        return text(locale, "home.text");
    }

    public InlineKeyboardMarkup homeKeyboard(Locale locale, boolean admin) {
        List<InlineKeyboardRow> rows = new ArrayList<>(List.of(
                new InlineKeyboardRow(button(text(locale, "btn.add"), "m:add")),
                new InlineKeyboardRow(button(text(locale, "btn.list"), "m:list"), button(text(locale, "btn.history"), "m:hist")),
                new InlineKeyboardRow(button(text(locale, "btn.help"), "m:help"), button(text(locale, "btn.lang"), "m:lang"))));
        if (admin) {
            rows.add(new InlineKeyboardRow(button(text(locale, "btn.admin"), "a:menu")));
        }
        return new InlineKeyboardMarkup(rows);
    }

    public String adminMenuText(Locale locale) {
        return text(locale, "admin.title");
    }

    public InlineKeyboardMarkup adminMenuKeyboard(Locale locale) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button(text(locale, "btn.adminTest"), "a:test")),
                new InlineKeyboardRow(button(text(locale, "btn.adminCheck"), "a:check")),
                new InlineKeyboardRow(button(text(locale, "btn.backToMenu"), "m:home"))));
    }

    public String adminTestRunning(Locale locale, int siteCount) {
        return text(locale, "admin.test.running", siteCount);
    }

    public String adminTestReport(Locale locale, String report) {
        return text(locale, "admin.test.header") + "\n\n" + report;
    }

    public String adminCheckRunning(Locale locale) {
        return text(locale, "admin.check.running");
    }

    public String adminCheckDone(Locale locale) {
        return text(locale, "admin.check.done");
    }

    public String adminBusy(Locale locale) {
        return text(locale, "admin.busy");
    }

    public String helpText(Locale locale) {
        return text(locale, "help.text", supportedSites());
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
        return text(locale, "url.prompt", supportedSites());
    }

    public String urlUnsupported(Locale locale) {
        return text(locale, "url.unsupported", supportedSites());
    }

    private String supportedSites() {
        return Arrays.stream(Site.values())
                .map(Site::getDomain)
                .collect(Collectors.joining(", "));
    }

    public String urlParseFailed(Locale locale) {
        return text(locale, "url.parseFailed");
    }

    public String productPreview(Locale locale, ProductSnapshot product) {
        return previewHeader(locale, product) + "\n\n" + text(locale, "choose.size");
    }

    public String variantPrompt(Locale locale, ProductSnapshot product) {
        return previewHeader(locale, product) + "\n\n" + text(locale, "variant.prompt");
    }

    public InlineKeyboardMarkup variantKeyboard(Locale locale, List<ProductSnapshot.VariantGroup> groups) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            List<ProductSnapshot.VariantOption> options = groups.get(groupIndex).options();
            InlineKeyboardRow currentRow = new InlineKeyboardRow();
            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                ProductSnapshot.VariantOption option = options.get(optionIndex);
                String label = (option.selected() ? "✅ " : "") + option.label();
                currentRow.add(button(label, "w:vr:" + groupIndex + ":" + optionIndex));
                if (currentRow.size() == VARIANT_BUTTONS_PER_ROW) {
                    rows.add(currentRow);
                    currentRow = new InlineKeyboardRow();
                }
            }
            if (!currentRow.isEmpty()) {
                rows.add(currentRow);
            }
        }
        rows.add(new InlineKeyboardRow(button(text(locale, "btn.next"), "w:vrok")));
        rows.add(new InlineKeyboardRow(button(text(locale, "btn.backToMenu"), "m:home")));
        return new InlineKeyboardMarkup(rows);
    }

    private String previewHeader(Locale locale, ProductSnapshot product) {
        StringBuilder result = new StringBuilder(product.title()).append("\n");
        result.append(text(locale, "price.label", PriceFormat.format(product.price())));
        if (product.regularPrice() != null) {
            int discount = PriceFormat.percentOf(product.regularPrice().subtract(product.price()), product.regularPrice());
            result.append(" ").append(text(locale, "price.was", PriceFormat.format(product.regularPrice()), discount));
        }
        if (product.stats() != null) {
            result.append("\n").append(statsLine(locale, product.stats()));
        }
        return result.toString();
    }

    public String statsLine(Locale locale, ProductSnapshot.AggregateStats stats) {
        if (stats.avgPrice() == null) {
            return text(locale, "stats.lineNoAvg", PriceFormat.format(stats.minPrice()),
                    PriceFormat.format(stats.maxPrice()), stats.offerCount());
        }
        return text(locale, "stats.line", PriceFormat.format(stats.minPrice()),
                PriceFormat.format(stats.avgPrice()), PriceFormat.format(stats.maxPrice()), stats.offerCount());
    }

    public InlineKeyboardMarkup sizeKeyboard(Locale locale, List<SizeOffer> sizes, int page) {
        // Shop offers (differing prices) read better one per row with the price shown
        boolean showPrices = hasVaryingPrices(sizes);
        List<InlineKeyboardRow> rows = showPrices ? pagedOfferRows(locale, sizes, page) : sizeGridRows(locale, sizes);
        rows.add(new InlineKeyboardRow(button(text(locale, "btn.backToMenu"), "m:home")));
        return new InlineKeyboardMarkup(rows);
    }

    private List<InlineKeyboardRow> pagedOfferRows(Locale locale, List<SizeOffer> sizes, int page) {
        int totalPages = (sizes.size() + OFFERS_PER_PAGE - 1) / OFFERS_PER_PAGE;
        int currentPage = Math.clamp(page, 0, totalPages - 1);
        List<InlineKeyboardRow> rows = new ArrayList<>();
        int from = currentPage * OFFERS_PER_PAGE;
        for (int sizeIndex = from; sizeIndex < Math.min(from + OFFERS_PER_PAGE, sizes.size()); sizeIndex++) {
            rows.add(new InlineKeyboardRow(button(sizeButtonLabel(locale, sizes.get(sizeIndex), true), "w:sz:" + sizeIndex)));
        }
        if (totalPages > 1) {
            InlineKeyboardRow navigation = new InlineKeyboardRow();
            if (currentPage > 0) {
                navigation.add(button("⬅️", "w:szpg:" + (currentPage - 1)));
            }
            navigation.add(button((currentPage + 1) + "/" + totalPages, "w:szpg:" + currentPage));
            if (currentPage < totalPages - 1) {
                navigation.add(button("➡️", "w:szpg:" + (currentPage + 1)));
            }
            rows.add(navigation);
        }
        return rows;
    }

    private List<InlineKeyboardRow> sizeGridRows(Locale locale, List<SizeOffer> sizes) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow currentRow = new InlineKeyboardRow();
        for (int sizeIndex = 0; sizeIndex < sizes.size(); sizeIndex++) {
            currentRow.add(button(sizeButtonLabel(locale, sizes.get(sizeIndex), false), "w:sz:" + sizeIndex));
            if (currentRow.size() == SIZE_BUTTONS_PER_ROW) {
                rows.add(currentRow);
                currentRow = new InlineKeyboardRow();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }
        return rows;
    }

    private boolean hasVaryingPrices(List<SizeOffer> sizes) {
        return sizes.stream().map(SizeOffer::price).distinct().count() > 1;
    }

    private String sizeButtonLabel(Locale locale, SizeOffer size, boolean showPrice) {
        String label = displaySize(locale, size.label());
        if (showPrice) {
            label += " · " + PriceFormat.format(size.price());
        }
        return size.inStock() ? label : label + " ✗";
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
        String size = switch (item.getSizeLabel()) {
            case SizeOffer.ONE_SIZE_LABEL -> "";
            case SizeOffer.MIN_PRICE_LABEL -> " · min";
            case null -> "";
            default -> " · " + item.getSizeLabel();
        };
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
        return switch (sizeLabel) {
            case SizeOffer.ONE_SIZE_LABEL -> "";
            case SizeOffer.MIN_PRICE_LABEL -> " " + text(locale, "size.suffix", text(locale, "size.minPrice"));
            case null -> "";
            default -> " " + text(locale, "size.suffix", sizeLabel);
        };
    }

    private String displaySize(Locale locale, String sizeLabel) {
        return switch (sizeLabel) {
            case SizeOffer.ONE_SIZE_LABEL -> text(locale, "size.one");
            case SizeOffer.MIN_PRICE_LABEL -> text(locale, "size.minPrice");
            case null -> text(locale, "size.one");
            default -> sizeLabel;
        };
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
