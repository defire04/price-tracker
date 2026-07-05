package com.price.tracker.config;

import com.price.tracker.interfaces.telegram.TelegramUpdateConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@Slf4j
public class TelegramBotConfig {

    @Bean
    TelegramClient telegramClient(TrackerProperties trackerProperties) {
        return new OkHttpTelegramClient(trackerProperties.bot().token());
    }

    @Bean(destroyMethod = "close")
    TelegramBotsLongPollingApplication telegramBotsApplication() {
        return new TelegramBotsLongPollingApplication();
    }

    @Bean
    ApplicationRunner telegramBotRegistrar(TelegramBotsLongPollingApplication botsApplication,
                                           TrackerProperties trackerProperties,
                                           TelegramUpdateConsumer updateConsumer) {
        return args -> {
            if (!trackerProperties.bot().enabled()) {
                log.info("Telegram bot is disabled");
                return;
            }
            if (trackerProperties.bot().token().isBlank()) {
                log.warn("TELEGRAM_BOT_TOKEN is empty, bot is not started");
                return;
            }
            botsApplication.registerBot(trackerProperties.bot().token(), updateConsumer);
            log.info("Telegram bot registered, long polling started");
        };
    }
}
