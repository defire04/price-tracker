package com.price.tracker.domain.enums;

import java.net.URI;
import java.util.Optional;

public enum Site {
    INTERTOP("intertop.ua"),
    ANSWEAR("answear.ua"),
    STRADIVARIUS("stradivarius.com"),
    EK("ek.ua"),
    HOTLINE("hotline.ua"),
    ZARA("zara.com");

    private final String domain;

    Site(String domain) {
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }

    public static Optional<Site> fromUrl(String url) {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (host == null) {
            return Optional.empty();
        }
        for (Site site : values()) {
            if (host.equals(site.domain) || host.endsWith("." + site.domain)) {
                return Optional.of(site);
            }
        }
        return Optional.empty();
    }
}
