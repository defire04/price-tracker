package com.price.tracker.domain.service.parser;

import com.price.tracker.domain.enums.Site;
import com.price.tracker.exception.UnsupportedSiteException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SiteParserRegistry {

    private final Map<Site, SiteParser> parsersBySite;

    public SiteParserRegistry(List<SiteParser> parsers) {
        this.parsersBySite = new EnumMap<>(Site.class);
        parsers.forEach(parser -> parsersBySite.put(parser.site(), parser));
    }

    public SiteParser parserFor(Site site) {
        SiteParser parser = parsersBySite.get(site);
        if (parser == null) {
            throw new UnsupportedSiteException(site.name());
        }
        return parser;
    }
}
