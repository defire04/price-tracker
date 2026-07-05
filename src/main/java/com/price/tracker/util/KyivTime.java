package com.price.tracker.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class KyivTime {

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private KyivTime() {
    }

    public static String format(Instant instant) {
        return FORMATTER.format(instant.atZone(KYIV));
    }
}
