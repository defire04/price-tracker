package com.price.tracker.util;

import java.util.Locale;
import java.util.Set;

public final class BotLanguage {

    public static final String UKRAINIAN = "uk";
    public static final String RUSSIAN = "ru";
    public static final String ENGLISH = "en";
    public static final Set<String> SUPPORTED = Set.of(UKRAINIAN, RUSSIAN, ENGLISH);

    private BotLanguage() {
    }

    public static String resolve(String preferredLanguage, String telegramLanguageCode) {
        if (preferredLanguage != null && SUPPORTED.contains(preferredLanguage)) {
            return preferredLanguage;
        }
        if (telegramLanguageCode != null) {
            String baseLanguage = telegramLanguageCode.toLowerCase(Locale.ROOT).split("-")[0];
            if (SUPPORTED.contains(baseLanguage)) {
                return baseLanguage;
            }
        }
        return UKRAINIAN;
    }

    public static Locale toLocale(String language) {
        return Locale.of(language);
    }
}
