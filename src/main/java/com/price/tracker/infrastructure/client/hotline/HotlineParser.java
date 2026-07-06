package com.price.tracker.infrastructure.client.hotline;

import com.price.tracker.config.TrackerProperties;
import com.price.tracker.domain.enums.Site;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.service.parser.SiteParser;
import com.price.tracker.exception.ProductParsingException;
import com.price.tracker.infrastructure.client.jsonld.JsonLdProductExtractor;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hotline aggregator: one "size" is one shop offer. The full offer list exists
 * only inside the server-rendered NUXT state, not in the visible HTML.
 */
@Component
@RequiredArgsConstructor
public class HotlineParser implements SiteParser {

    private static final String HOTLINE_ORIGIN = "https://hotline.ua";
    private static final String NEW_CONDITION_PREFIX = "нов";

    private final TrackerProperties trackerProperties;
    private final JsonLdProductExtractor jsonLdProductExtractor;
    private final HotlineStateExtractor stateExtractor;

    @Override
    public Site site() {
        return Site.HOTLINE;
    }

    @Override
    public ProductSnapshot fetch(String url) {
        Document page;
        try {
            page = Jsoup.connect(url)
                    .userAgent(trackerProperties.http().userAgent())
                    .timeout(trackerProperties.http().timeoutMs())
                    .get();
        } catch (IOException e) {
            throw new ProductParsingException("Failed to load page: " + url, e);
        }
        return parse(page, url);
    }

    ProductSnapshot parse(Document page, String url) {
        JsonNode productJsonLd = jsonLdProductExtractor.findProduct(page);
        if (productJsonLd == null) {
            throw new ProductParsingException("No JSON-LD product found: " + url);
        }
        String title = productJsonLd.path("name").asString(page.title());
        JsonNode aggregateOffer = productJsonLd.path("offers");
        JsonNode lowPrice = aggregateOffer.path("lowPrice");
        if (!lowPrice.isNumber()) {
            throw new ProductParsingException("Hotline JSON-LD offer has no numeric lowPrice: " + url);
        }
        BigDecimal price = lowPrice.decimalValue();
        boolean inStock = aggregateOffer.path("availability").asString("").endsWith("InStock");
        JsonNode highPrice = aggregateOffer.path("highPrice");
        ProductSnapshot.AggregateStats stats = new ProductSnapshot.AggregateStats(
                price,
                null,
                highPrice.isNumber() ? highPrice.decimalValue() : null,
                aggregateOffer.path("offerCount").asInt(0));
        List<SizeOffer> offers = new ArrayList<>();
        offers.add(new SizeOffer(SizeOffer.MIN_PRICE_LABEL, price, null, inStock));
        offers.addAll(shopOffers(page.html()));
        return new ProductSnapshot(title, price, null, inStock, offers, stats);
    }

    private List<SizeOffer> shopOffers(String html) {
        List<HotlineStateExtractor.HotlineOffer> stateOffers = stateExtractor.extractOffers(html).stream()
                .filter(this::isNewCondition)
                .sorted(Comparator.comparing(HotlineStateExtractor.HotlineOffer::price))
                .toList();
        Map<String, SizeOffer> offersByShop = new LinkedHashMap<>();
        for (HotlineStateExtractor.HotlineOffer offer : stateOffers) {
            offersByShop.putIfAbsent(offer.firmTitle(), new SizeOffer(
                    offer.firmTitle(), offer.price(), null, true,
                    offer.conversionUrl() != null ? HOTLINE_ORIGIN + offer.conversionUrl() : null));
        }
        return new ArrayList<>(offersByShop.values());
    }

    private boolean isNewCondition(HotlineStateExtractor.HotlineOffer offer) {
        return offer.condition() == null
                || offer.condition().toLowerCase(Locale.ROOT).startsWith(NEW_CONDITION_PREFIX);
    }
}
