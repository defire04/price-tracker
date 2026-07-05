package com.price.tracker.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class PriceFormat {

    private static final Locale UKRAINIAN = Locale.of("uk", "UA");

    private PriceFormat() {
    }

    public static String format(BigDecimal price) {
        return NumberFormat.getNumberInstance(UKRAINIAN).format(price) + " ₴";
    }

    public static int percentOf(BigDecimal part, BigDecimal base) {
        return part.multiply(BigDecimal.valueOf(100))
                .divide(base, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
