package com.price.tracker.infrastructure.client.zara;

import com.microsoft.playwright.PlaywrightException;
import com.price.tracker.domain.enums.Site;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.service.parser.SiteParser;
import com.price.tracker.exception.ProductParsingException;
import com.price.tracker.infrastructure.client.playwright.PlaywrightPageFetcher;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zara is behind Akamai and renders from window.zara.viewPayload: every color of the
 * product with its own sizes, stock and prices in kopecks. One color is one wizard
 * variant; the chosen one is kept in the tracked url as Zara's own ?v1=productId.
 */
@Component
@RequiredArgsConstructor
public class ZaraParser implements SiteParser {

    private static final String VIEW_PAYLOAD_MARKER = "window.zara.viewPayload = ";
    private static final String READY_EXPRESSION = "() => window.zara !== undefined && window.zara.viewPayload !== undefined";
    private static final Pattern COLOR_ID_IN_URL = Pattern.compile("[?&]v1=(\\d+)");
    // low_on_stock is still buyable; coming_soon and back_soon are not
    private static final Set<String> IN_STOCK_AVAILABILITY_VALUES = Set.of("in_stock", "low_on_stock");
    private static final BigDecimal KOPECKS_IN_HRYVNIA = BigDecimal.valueOf(100);

    private final PlaywrightPageFetcher pageFetcher;
    // Jackson 3 rejects anything after the value by default, and the payload is followed by more JS
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    @Override
    public Site site() {
        return Site.ZARA;
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
        JsonNode product = findProduct(page);
        if (product == null) {
            throw new ProductParsingException("No Zara viewPayload product found: " + url);
        }
        JsonNode colors = product.path("detail").path("colors");
        if (colors.isEmpty()) {
            throw new ProductParsingException("Zara product has no colors: " + url);
        }
        String colorId = colorIdFrom(url);
        JsonNode color = selectedColor(colors, colorId);
        BigDecimal price = hryvnias(color.path("price"));
        if (price == null) {
            throw new ProductParsingException("Zara color has no numeric price: " + url);
        }
        BigDecimal regularPrice = regularPrice(color.path("oldPrice"), price);
        List<SizeOffer> sizes = extractSizes(color.path("sizes"), price, regularPrice);
        if (sizes.isEmpty()) {
            throw new ProductParsingException("Zara color has no sizes: " + url);
        }
        String title = product.path("name").asString(page.title());
        if (colors.size() > 1) {
            title = title + " · " + color.path("name").asString("");
        }
        boolean anySizeInStock = sizes.stream().anyMatch(SizeOffer::inStock);
        return new ProductSnapshot(title, price, regularPrice, anySizeInStock, sizes, null,
                variantGroups(colors, stripQuery(url), color));
    }

    private JsonNode findProduct(Document page) {
        for (Element script : page.select("script")) {
            String scriptText = script.data();
            int markerIndex = scriptText.indexOf(VIEW_PAYLOAD_MARKER);
            if (markerIndex < 0) {
                continue;
            }
            JsonNode payload = readFirstJsonValue(scriptText.substring(markerIndex + VIEW_PAYLOAD_MARKER.length()));
            return payload != null && payload.hasNonNull("product") ? payload.get("product") : null;
        }
        return null;
    }

    private JsonNode readFirstJsonValue(String json) {
        try (JsonParser parser = objectMapper.createParser(json)) {
            return objectMapper.readTree(parser);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Without ?v1 the page shows the first color, so the parser does the same
    private JsonNode selectedColor(JsonNode colors, String colorId) {
        if (colorId != null) {
            for (JsonNode color : colors) {
                if (colorId.equals(color.path("productId").asString(""))) {
                    return color;
                }
            }
        }
        return colors.get(0);
    }

    private BigDecimal regularPrice(JsonNode oldPrice, BigDecimal price) {
        BigDecimal regularPrice = hryvnias(oldPrice);
        return regularPrice == null || regularPrice.compareTo(price) == 0 ? null : regularPrice;
    }

    private List<SizeOffer> extractSizes(JsonNode sizes, BigDecimal colorPrice, BigDecimal colorRegularPrice) {
        List<SizeOffer> offers = new ArrayList<>();
        for (JsonNode size : sizes) {
            String label = size.path("name").asString("").trim();
            if (label.isEmpty()) {
                label = SizeOffer.ONE_SIZE_LABEL;
            }
            BigDecimal sizePrice = hryvnias(size.path("price"));
            BigDecimal price = sizePrice != null ? sizePrice : colorPrice;
            BigDecimal regularPrice = sizePrice != null ? regularPrice(size.path("oldPrice"), sizePrice) : colorRegularPrice;
            boolean inStock = IN_STOCK_AVAILABILITY_VALUES.contains(size.path("availability").asString(""));
            offers.add(new SizeOffer(label, price, regularPrice, inStock));
        }
        return offers;
    }

    private List<ProductSnapshot.VariantGroup> variantGroups(JsonNode colors, String productUrl, JsonNode selected) {
        if (colors.size() < 2) {
            return List.of();
        }
        List<ProductSnapshot.VariantOption> options = new ArrayList<>();
        for (JsonNode color : colors) {
            options.add(new ProductSnapshot.VariantOption(
                    color.path("name").asString(""),
                    productUrl + "?v1=" + color.path("productId").asString(""),
                    color == selected));
        }
        return List.of(new ProductSnapshot.VariantGroup("color", options));
    }

    private BigDecimal hryvnias(JsonNode kopecks) {
        return kopecks.isNumber() ? kopecks.decimalValue().divide(KOPECKS_IN_HRYVNIA) : null;
    }

    private static String colorIdFrom(String url) {
        Matcher matcher = COLOR_ID_IN_URL.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String stripQuery(String url) {
        int queryStart = url.indexOf('?');
        return queryStart < 0 ? url : url.substring(0, queryStart);
    }
}
