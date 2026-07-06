package com.price.tracker.infrastructure.client.answear;

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
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AnswearParser implements SiteParser {

    /**
     * Product data lives in the React Query dehydrated state inlined by the SSR;
     * per-size stock exists only there (JSON-LD has no per-size availability).
     */
    private static final String REACT_QUERY_STATE_MARKER = "window.__REACT_QUERY_STATE__ = ";
    private static final String READY_EXPRESSION = "() => window.__REACT_QUERY_STATE__ !== undefined";
    // "LAST" means last piece in stock — still buyable
    private static final Set<String> IN_STOCK_AVAILABILITY_VALUES = Set.of("IN_STOCK", "LAST");

    private final PlaywrightPageFetcher pageFetcher;
    private final JsonLdProductExtractor jsonLdProductExtractor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Site site() {
        return Site.ANSWEAR;
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
        JsonNode productData = findProductData(page);
        if (productData == null) {
            return jsonLdFallback(page, url);
        }
        JsonNode product = productData.get("product");
        String title = product.path("name").asString(page.title());
        BigDecimal price = extractPrice(product, url);
        BigDecimal regularPrice = extractRegularPrice(product, price);
        List<SizeOffer> sizes = extractSizes(product.path("allSizes"), price, regularPrice);
        if (sizes.isEmpty()) {
            boolean inStock = productData.path("availableForBuy").asBoolean(false);
            sizes = List.of(new SizeOffer(SizeOffer.ONE_SIZE_LABEL, price, regularPrice, inStock));
        }
        boolean anySizeInStock = sizes.stream().anyMatch(SizeOffer::inStock);
        return new ProductSnapshot(title, price, regularPrice, anySizeInStock, sizes);
    }

    private JsonNode findProductData(Document page) {
        for (Element script : page.select("script")) {
            String scriptText = script.data();
            int markerIndex = scriptText.indexOf(REACT_QUERY_STATE_MARKER);
            if (markerIndex < 0) {
                continue;
            }
            JsonNode state = readFirstJsonValue(scriptText.substring(markerIndex + REACT_QUERY_STATE_MARKER.length()));
            if (state == null) {
                return null;
            }
            for (JsonNode query : state.path("queries")) {
                JsonNode data = query.path("state").path("data");
                if (data.hasNonNull("product")) {
                    return data;
                }
            }
        }
        return null;
    }

    // The state assignment is followed by more JS, so read a single JSON value and stop
    private JsonNode readFirstJsonValue(String json) {
        try (JsonParser parser = objectMapper.createParser(json)) {
            return objectMapper.readTree(parser);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private BigDecimal extractPrice(JsonNode product, String url) {
        JsonNode price = product.path("price");
        if (!price.isNumber()) {
            throw new ProductParsingException("Answear product has no numeric price: " + url);
        }
        return price.decimalValue();
    }

    private BigDecimal extractRegularPrice(JsonNode product, BigDecimal price) {
        if (!product.path("priceIsDiscounted").asBoolean(false)) {
            return null;
        }
        JsonNode regularPrice = product.path("priceRegular");
        if (!regularPrice.isNumber() || regularPrice.decimalValue().compareTo(price) == 0) {
            return null;
        }
        return regularPrice.decimalValue();
    }

    private List<SizeOffer> extractSizes(JsonNode allSizes, BigDecimal price, BigDecimal regularPrice) {
        List<SizeOffer> sizes = new ArrayList<>();
        for (JsonNode size : allSizes) {
            String label = size.path("name").asString("").trim();
            if (label.isEmpty()) {
                label = SizeOffer.ONE_SIZE_LABEL;
            }
            String availability = size.path("variation").path("availability").asString("");
            boolean inStock = IN_STOCK_AVAILABILITY_VALUES.contains(availability);
            sizes.add(new SizeOffer(label, price, regularPrice, inStock));
        }
        return sizes;
    }

    private ProductSnapshot jsonLdFallback(Document page, String url) {
        JsonNode productJsonLd = jsonLdProductExtractor.findProduct(page);
        if (productJsonLd == null) {
            throw new ProductParsingException("Neither react-query state nor JSON-LD product found: " + url);
        }
        String title = productJsonLd.path("name").asString(page.title());
        List<SizeOffer> sizes = jsonLdProductExtractor.oneSizeOffers(productJsonLd, url);
        SizeOffer offer = sizes.getFirst();
        return new ProductSnapshot(title, offer.price(), offer.regularPrice(), offer.inStock(), sizes);
    }
}
