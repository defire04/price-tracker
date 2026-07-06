package com.price.tracker.infrastructure.client.telegram;

import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.entity.User;
import com.price.tracker.domain.model.PriceAlert;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.service.notification.NotificationSender;
import com.price.tracker.util.BotLanguage;
import com.price.tracker.util.KyivTime;
import com.price.tracker.util.PriceFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationSender implements NotificationSender {

    private final TelegramClient telegramClient;
    private final MessageSource messageSource;

    @Override
    public void send(long telegramChatId, PriceAlert alert) {
        Locale locale = localeOf(alert.item().getUser());
        StringBuilder text = new StringBuilder(headline(locale, alert)).append("\n")
                .append(itemLine(locale, alert.item())).append("\n");
        if (alert.stats() != null) {
            text.append(statsLine(locale, alert.stats())).append("\n");
        }
        text.append(KyivTime.format(Instant.now())).append("\n")
                .append(alert.item().getUrl());
        if (alert.offerUrl() != null) {
            text.append("\n").append(message(locale, "notif.shopLink", alert.offerUrl()));
        }
        try {
            telegramClient.execute(SendMessage.builder().chatId(telegramChatId).text(text.toString()).build());
        } catch (TelegramApiException e) {
            log.error("Failed to send alert to chat {}", telegramChatId, e);
        }
    }

    private String statsLine(Locale locale, ProductSnapshot.AggregateStats stats) {
        if (stats.avgPrice() == null) {
            return message(locale, "stats.lineNoAvg", PriceFormat.format(stats.minPrice()),
                    PriceFormat.format(stats.maxPrice()), stats.offerCount());
        }
        return message(locale, "stats.line", PriceFormat.format(stats.minPrice()),
                PriceFormat.format(stats.avgPrice()), PriceFormat.format(stats.maxPrice()), stats.offerCount());
    }

    private Locale localeOf(User user) {
        return BotLanguage.toLocale(BotLanguage.resolve(user.getPreferredLanguage(), user.getLanguageCode()));
    }

    private String headline(Locale locale, PriceAlert alert) {
        return switch (alert.type()) {
            case RESTOCK -> message(locale, "notif.RESTOCK", PriceFormat.format(alert.newPrice()));
            case PRICE_DROP -> message(locale, "notif.PRICE_DROP", priceChange(alert));
            case PRICE_INCREASE -> message(locale, "notif.PRICE_INCREASE", priceChange(alert));
            case DISCOUNT_PERCENT -> message(locale, "notif.DISCOUNT_PERCENT",
                    alert.discountPercent().setScale(0, RoundingMode.HALF_UP),
                    priceChange(alert),
                    thresholdText(alert.item()));
        };
    }

    private String message(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    private String priceChange(PriceAlert alert) {
        String change = PriceFormat.format(alert.oldPrice()) + " → " + PriceFormat.format(alert.newPrice());
        BigDecimal difference = alert.newPrice().subtract(alert.oldPrice());
        if (alert.oldPrice().compareTo(BigDecimal.ZERO) > 0 && difference.signum() != 0) {
            int percent = PriceFormat.percentOf(difference.abs(), alert.oldPrice());
            change += " (" + (difference.signum() < 0 ? "-" : "+") + percent + "%)";
        }
        return change;
    }

    private String thresholdText(TrackedItem item) {
        return item.getDiscountThreshold() == null
                ? ""
                : item.getDiscountThreshold().stripTrailingZeros().toPlainString();
    }

    private String itemLine(Locale locale, TrackedItem item) {
        return switch (item.getSizeLabel()) {
            case SizeOffer.ONE_SIZE_LABEL -> item.displayTitle();
            case SizeOffer.MIN_PRICE_LABEL ->
                    item.displayTitle() + " " + message(locale, "size.suffix", message(locale, "size.minPrice"));
            case null -> item.displayTitle();
            default -> item.displayTitle() + " " + message(locale, "size.suffix", item.getSizeLabel());
        };
    }
}
