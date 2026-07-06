package com.price.tracker.infrastructure.client.stradivarius;

import com.microsoft.playwright.PlaywrightException;
import com.price.tracker.domain.enums.Site;
import com.price.tracker.domain.model.ProductSnapshot;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StradivariusParser implements SiteParser {

    /**
     * The page is a client-rendered SPA behind Akamai: JSON-LD gives title and price,
     * but per-size stock exists only in the rendered size selector, so we wait for it.
     */
    private static final String READY_EXPRESSION = "() => document.querySelector('[data-testid=\"sizes-list\"]') !== null";
    private static final String SIZE_ITEM_SELECTOR = "[data-testid=size-item]";
    private static final String SIZE_NAME_SELECTOR = "[data-testid=size-name]";
    private static final String OUT_OF_STOCK_CLASS = "size-no-stock";
    // Scoped to the PDP price block: product grids elsewhere on the page reuse .oldPrice
    private static final String OLD_PRICE_SELECTOR = ".product-price-info .oldPrice";

    private final PlaywrightPageFetcher pageFetcher;
    private final JsonLdProductExtractor jsonLdProductExtractor;

    @Override
    public Site site() {
        return Site.STRADIVARIUS;
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
        if (productJsonLd == null) {
            throw new ProductParsingException("No JSON-LD product found: " + url);
        }
        String title = productJsonLd.path("name").asString(page.title());
        BigDecimal price = extractPrice(productJsonLd, url);
        BigDecimal regularPrice = extractRegularPrice(page, price);
        List<SizeOffer> sizes = extractSizes(page, price, regularPrice);
        if (sizes.isEmpty()) {
            boolean inStock = productJsonLd.path("offers").path("availability").asString("").endsWith("InStock");
            sizes = List.of(new SizeOffer(SizeOffer.ONE_SIZE_LABEL, price, regularPrice, inStock));
        }
        boolean anySizeInStock = sizes.stream().anyMatch(SizeOffer::inStock);
        return new ProductSnapshot(title, price, regularPrice, anySizeInStock, sizes);
    }

    // JSON-LD offer price is a string here ("1899"), unlike the numeric prices on other sites
    private BigDecimal extractPrice(JsonNode productJsonLd, String url) {
        String price = productJsonLd.path("offers").path("price").asString("");
        if (price.isEmpty()) {
            throw new ProductParsingException("Stradivarius JSON-LD offer has no price: " + url);
        }
        try {
            return new BigDecimal(price);
        } catch (NumberFormatException e) {
            throw new ProductParsingException("Unparseable Stradivarius price '" + price + "': " + url, e);
        }
    }

    private BigDecimal extractRegularPrice(Document page, BigDecimal price) {
        Element oldPrice = page.selectFirst(OLD_PRICE_SELECTOR);
        if (oldPrice == null) {
            return null;
        }
        String digits = oldPrice.text().replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        BigDecimal regularPrice = new BigDecimal(digits);
        return regularPrice.compareTo(price) == 0 ? null : regularPrice;
    }

    private List<SizeOffer> extractSizes(Document page, BigDecimal price, BigDecimal regularPrice) {
        List<SizeOffer> sizes = new ArrayList<>();
        for (Element sizeItem : page.select(SIZE_ITEM_SELECTOR)) {
            Element sizeName = sizeItem.selectFirst(SIZE_NAME_SELECTOR);
            if (sizeName == null) {
                continue;
            }
            String label = sizeName.attr("data-text").trim();
            if (label.isEmpty()) {
                label = sizeName.text().trim();
            }
            if (label.isEmpty()) {
                label = SizeOffer.ONE_SIZE_LABEL;
            }
            boolean inStock = !sizeItem.hasClass(OUT_OF_STOCK_CLASS);
            sizes.add(new SizeOffer(label, price, regularPrice, inStock));
        }
        return sizes;
    }
}
