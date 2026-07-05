package com.price.tracker.infrastructure.client.telegram;

import com.price.tracker.domain.model.PriceAlert;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.entity.TrackedItem;
import com.price.tracker.domain.service.notification.NotificationSender;
import com.price.tracker.util.KyivTime;
import com.price.tracker.util.PriceFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationSender implements NotificationSender {

    private final TelegramClient telegramClient;

    @Override
    public void send(long telegramChatId, PriceAlert alert) {
        String text = headline(alert) + "\n"
                + itemLine(alert.item()) + "\n"
                + KyivTime.format(Instant.now()) + "\n"
                + alert.item().getUrl();
        try {
            telegramClient.execute(SendMessage.builder().chatId(telegramChatId).text(text).build());
        } catch (TelegramApiException e) {
            log.error("Failed to send alert to chat {}", telegramChatId, e);
        }
    }

    private String headline(PriceAlert alert) {
        return switch (alert.type()) {
            case RESTOCK -> "🔔 Знову в наявності! Ціна: " + PriceFormat.format(alert.newPrice());
            case PRICE_DROP -> "📉 Ціна знизилась: " + priceChange(alert);
            case PRICE_INCREASE -> "📈 Ціна зросла: " + priceChange(alert);
            case DISCOUNT_PERCENT -> "🏷 Знижка досягла " + alert.discountPercent().setScale(0, RoundingMode.HALF_UP)
                    + "% (" + priceChange(alert) + "), поріг " + thresholdText(alert.item());
        };
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
                : item.getDiscountThreshold().stripTrailingZeros().toPlainString() + "%";
    }

    private String itemLine(TrackedItem item) {
        String sizeSuffix = SizeOffer.ONE_SIZE_LABEL.equals(item.getSizeLabel())
                ? ""
                : " — розмір " + item.getSizeLabel();
        return item.getTitle() + sizeSuffix;
    }
}
