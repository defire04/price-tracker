package com.price.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tracker")
public record TrackerProperties(Bot bot, Check check, Http http) {

    public record Bot(boolean enabled, String token) {
    }

    public record Check(String cron, long jitterMinMs, long jitterMaxMs) {
    }

    public record Http(String userAgent, int timeoutMs) {
    }
}
