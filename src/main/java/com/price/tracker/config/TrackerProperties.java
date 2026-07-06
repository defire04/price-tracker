package com.price.tracker.config;

import com.price.tracker.domain.enums.Site;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("tracker")
public record TrackerProperties(Bot bot, Check check, Http http, Map<Site, String> testUrls) {

    public record Bot(boolean enabled, String token, long adminChatId) {
    }

    public record Check(String cron, long jitterMinMs, long jitterMaxMs) {
    }

    public record Http(String userAgent, int timeoutMs) {
    }
}
