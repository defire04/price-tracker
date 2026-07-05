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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class TrackerMessages {

    private static final int SIZE_BUTTONS_PER_ROW = 4;
    private static final int ITEM_BUTTON_TITLE_LENGTH = 32;

    private TrackerMessages() {
    }

    public static String homeText() {
        return """
                Привіт! Я стежу за цінами та наявністю товарів на Intertop.

                Що робимо?""";
    }

    public static InlineKeyboardMarkup homeKeyboard() {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button("➕ Додати відстеження", "m:add")),
                new InlineKeyboardRow(button("📋 Мої відстеження", "m:list")),
                new InlineKeyboardRow(button("ℹ Довідка", "m:help"))));
    }

    public static String helpText() {
        return """
                Як це працює:
                1. Тисни «➕ Додати відстеження» і надішли посилання на товар з intertop.ua
                2. Обери розмір і події: поява в наявності, зниження/зростання ціни, знижка до порогу
                3. Я перевіряю сайт кілька разів на день і надішлю повідомлення, коли щось зміниться

                Відстеження живуть у «📋 Мої відстеження» — там можна перейменувати або зупинити.""";
    }

    public static InlineKeyboardMarkup backToMenuKeyboard() {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button("⬅ Меню", "m:home"))));
    }

    public static String urlPrompt() {
        return "Надішли посилання на товар з intertop.ua:";
    }

    public static String productPreview(ProductSnapshot product) {
        StringBuilder text = new StringBuilder(product.title()).append("\n");
        text.append("Ціна: ").append(PriceFormat.format(product.price()));
        if (product.regularPrice() != null) {
            int discount = PriceFormat.percentOf(product.regularPrice().subtract(product.price()), product.regularPrice());
            text.append(" (було ").append(PriceFormat.format(product.regularPrice()))
                    .append(", -").append(discount).append("%)");
        }
        text.append("\n\nОбери розмір (✗ — немає в наявності):");
        return text.toString();
    }

    public static InlineKeyboardMarkup sizeKeyboard(List<SizeOffer> sizes) {
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
        rows.add(new InlineKeyboardRow(button("⬅ Меню", "m:home")));
        return new InlineKeyboardMarkup(rows);
    }

    public static String eventsPrompt(String sizeLabel) {
        return "Розмір: " + displaySize(sizeLabel) + "\nОбери події (можна декілька):";
    }

    public static InlineKeyboardMarkup eventsKeyboard(Set<TrackedEventType> selectedEvents) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(eventButton(TrackedEventType.RESTOCK, "R", selectedEvents)),
                new InlineKeyboardRow(eventButton(TrackedEventType.PRICE_DROP, "PD", selectedEvents)),
                new InlineKeyboardRow(eventButton(TrackedEventType.PRICE_INCREASE, "PI", selectedEvents)),
                new InlineKeyboardRow(eventButton(TrackedEventType.DISCOUNT_PERCENT, "DP", selectedEvents)),
                new InlineKeyboardRow(button("⬅ Назад", "w:back:sz"), button("✅ Готово", "w:ok"))));
    }

    public static String thresholdPrompt() {
        return "Введи поріг знижки у відсотках, наприклад 30:";
    }

    public static InlineKeyboardMarkup thresholdKeyboard() {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button("⬅ Назад", "w:back:ev"))));
    }

    public static String trackingCreated(TrackedItem item) {
        return "✅ Стежу!\n"
                + item.displayTitle() + sizeSuffix(item.getSizeLabel()) + "\n"
                + "Події: " + formatEvents(item);
    }

    public static InlineKeyboardMarkup trackingCreatedKeyboard() {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button("➕ Додати ще", "m:add"), button("📋 Мої відстеження", "m:list")),
                new InlineKeyboardRow(button("🏠 Меню", "m:home"))));
    }

    public static String menuListText(List<TrackedItem> items) {
        if (items.isEmpty()) {
            return "Активних відстежень немає.";
        }
        return "📋 Твої відстеження (" + items.size() + ").\nОбери, щоб побачити деталі:";
    }

    public static InlineKeyboardMarkup menuListKeyboard(List<TrackedItem> items) {
        List<InlineKeyboardRow> rows = items.stream()
                .map(item -> new InlineKeyboardRow(button(menuItemButtonLabel(item), "m:item:" + item.getId())))
                .collect(Collectors.toCollection(ArrayList::new));
        rows.add(new InlineKeyboardRow(button("➕ Додати", "m:add"), button("🔄 Оновити", "m:list")));
        rows.add(new InlineKeyboardRow(button("🏠 Меню", "m:home")));
        return new InlineKeyboardMarkup(rows);
    }

    public static String menuItemText(TrackingDetails details) {
        TrackedItem item = details.item();
        PriceSnapshot lastSnapshot = details.lastSnapshot();
        StringBuilder text = new StringBuilder(item.displayTitle())
                .append(sizeSuffix(item.getSizeLabel())).append("\n");
        if (lastSnapshot != null) {
            text.append("Ціна: ").append(PriceFormat.format(lastSnapshot.getPrice()));
            if (lastSnapshot.getRegularPrice() != null) {
                text.append(" (було ").append(PriceFormat.format(lastSnapshot.getRegularPrice())).append(")");
            }
            text.append("\nНаявність: ").append(lastSnapshot.isInStock() ? "✅ є" : "❌ немає").append("\n");
            text.append("Перевірено: ").append(KyivTime.format(lastSnapshot.getCheckedAt())).append("\n");
        }
        text.append("Події: ").append(formatEvents(item)).append("\n");
        text.append(item.getUrl());
        return text.toString();
    }

    public static InlineKeyboardMarkup menuItemKeyboard(long itemId) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button("✏ Перейменувати", "m:ren:" + itemId), button("⏹ Зупинити", "m:stop:" + itemId)),
                new InlineKeyboardRow(button("⬅ Назад", "m:list"))));
    }

    public static String renamePrompt(TrackedItem item) {
        return "Поточна назва:\n" + item.displayTitle() + "\n\nНадішли нову назву:";
    }

    public static InlineKeyboardMarkup renameKeyboard(long itemId) {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button("⬅ Назад", "m:item:" + itemId))));
    }

    public static String eventLabel(TrackedEventType eventType) {
        return switch (eventType) {
            case RESTOCK -> "поява в наявності";
            case PRICE_DROP -> "зниження ціни";
            case PRICE_INCREASE -> "зростання ціни";
            case DISCOUNT_PERCENT -> "знижка до порогу";
        };
    }

    private static String menuItemButtonLabel(TrackedItem item) {
        String title = item.displayTitle();
        if (title.length() > ITEM_BUTTON_TITLE_LENGTH) {
            title = title.substring(0, ITEM_BUTTON_TITLE_LENGTH) + "…";
        }
        String size = SizeOffer.ONE_SIZE_LABEL.equals(item.getSizeLabel()) ? "" : " · " + item.getSizeLabel();
        return title + size;
    }

    private static String formatEvents(TrackedItem item) {
        return item.getEvents().stream()
                .filter(TrackedItemEvent::isActive)
                .map(event -> eventDisplay(event, item))
                .collect(Collectors.joining(", "));
    }

    private static String eventDisplay(TrackedItemEvent event, TrackedItem item) {
        if (event.getEventType() == TrackedEventType.DISCOUNT_PERCENT && item.getDiscountThreshold() != null) {
            return "знижка ≥ " + item.getDiscountThreshold().stripTrailingZeros().toPlainString() + "%";
        }
        return eventLabel(event.getEventType());
    }

    private static String sizeSuffix(String sizeLabel) {
        return SizeOffer.ONE_SIZE_LABEL.equals(sizeLabel) ? "" : " — розмір " + sizeLabel;
    }

    private static String displaySize(String sizeLabel) {
        return SizeOffer.ONE_SIZE_LABEL.equals(sizeLabel) ? "єдиний" : sizeLabel;
    }

    private static InlineKeyboardButton eventButton(TrackedEventType eventType,
                                                    String code,
                                                    Set<TrackedEventType> selectedEvents) {
        String prefix = selectedEvents.contains(eventType) ? "✅ " : "";
        return button(prefix + eventLabel(eventType), "w:ev:" + code);
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }
}
