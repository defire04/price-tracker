package com.price.tracker.infrastructure.client.intertop;

import com.microsoft.playwright.PlaywrightException;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.enums.Site;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.service.parser.SiteParser;
import com.price.tracker.exception.ProductParsingException;
import com.price.tracker.infrastructure.client.jsonld.JsonLdProductExtractor;
import com.price.tracker.infrastructure.client.playwright.PlaywrightPageFetcher;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Component
@RequiredArgsConstructor
public class IntertopParser implements SiteParser {

    private static final String NUXT_DATA_SELECTOR = "script#__NUXT_DATA__";
    // Cloudflare answers plain HTTP with a JS challenge (403), so the page is loaded in a browser
    private static final String READY_EXPRESSION = "() => document.querySelector('" + NUXT_DATA_SELECTOR + "') !== null";

    private final PlaywrightPageFetcher pageFetcher;
    private final NuxtDataExtractor nuxtDataExtractor;
    private final JsonLdProductExtractor jsonLdProductExtractor;

    @Override
    public Site site() {
        return Site.INTERTOP;
    }

    @Override
    public ProductSnapshot fetch(String url) {
        String html;
        try {
            html = pageFetcher.fetchHtml(url, READY_EXPRESSION);
        } catch (PlaywrightException e) {
            throw new ProductParsingException("Failed to load page: " + url, e);
        }
        return parse(Jsoup.parse(html, url), url);
    }

    ProductSnapshot parse(Document page, String url) {
        JsonNode productJsonLd = jsonLdProductExtractor.findProduct(page);
        List<SizeOffer> sizes = extractSizes(page);
        if (sizes.isEmpty()) {
            sizes = oneSizeFallback(productJsonLd, url);
        }
        String title = extractTitle(page, productJsonLd);
        SizeOffer referenceOffer = sizes.stream()
                .filter(SizeOffer::inStock)
                .findFirst()
                .orElse(sizes.getFirst());
        boolean anySizeInStock = sizes.stream().anyMatch(SizeOffer::inStock);
        return new ProductSnapshot(title, referenceOffer.price(), referenceOffer.regularPrice(), anySizeInStock, sizes);
    }

    private List<SizeOffer> extractSizes(Document page) {
        Element nuxtDataScript = page.selectFirst(NUXT_DATA_SELECTOR);
        if (nuxtDataScript == null) {
            return List.of();
        }
        try {
            return nuxtDataExtractor.extractSizeOffers(nuxtDataScript.data());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<SizeOffer> oneSizeFallback(JsonNode productJsonLd, String url) {
        if (productJsonLd == null) {
            throw new ProductParsingException("Neither NUXT_DATA sizes nor JSON-LD product found: " + url);
        }
        return jsonLdProductExtractor.oneSizeOffers(productJsonLd, url);
    }

    private String extractTitle(Document page, JsonNode productJsonLd) {
        if (productJsonLd != null && productJsonLd.hasNonNull("name")) {
            return productJsonLd.get("name").asString();
        }
        return page.title();
    }
}
