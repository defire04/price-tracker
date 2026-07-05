package com.price.tracker.infrastructure.client.telegram;

import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.entity.User;
import com.price.tracker.domain.model.PriceAlert;
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
        String text = headline(locale, alert) + "\n"
                + itemLine(locale, alert.item()) + "\n"
                + KyivTime.format(Instant.now()) + "\n"
                + alert.item().getUrl();
        try {
            telegramClient.execute(SendMessage.builder().chatId(telegramChatId).text(text).build());
        } catch (TelegramApiException e) {
            log.error("Failed to send alert to chat {}", telegramChatId, e);
        }
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
        if (SizeOffer.ONE_SIZE_LABEL.equals(item.getSizeLabel())) {
            return item.displayTitle();
        }
        return item.displayTitle() + " " + message(locale, "size.suffix", item.getSizeLabel());
    }
}
