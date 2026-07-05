package com.price.tracker.infrastructure.client.intertop;

import com.price.tracker.config.TrackerProperties;
import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.enums.Site;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.domain.service.parser.SiteParser;
import com.price.tracker.exception.ProductParsingException;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class IntertopParser implements SiteParser {

    private static final String NUXT_DATA_SELECTOR = "script#__NUXT_DATA__";
    private static final String JSON_LD_SELECTOR = "script[type=application/ld+json]";

    private final TrackerProperties trackerProperties;
    private final NuxtDataExtractor nuxtDataExtractor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Site site() {
        return Site.INTERTOP;
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
        JsonNode productJsonLd = findProductJsonLd(page);
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
        JsonNode offer = productJsonLd.path("offers");
        JsonNode price = offer.path("price");
        if (!price.isNumber()) {
            throw new ProductParsingException("JSON-LD offer has no numeric price: " + url);
        }
        boolean inStock = offer.path("availability").asText("").endsWith("InStock");
        return List.of(new SizeOffer(SizeOffer.ONE_SIZE_LABEL, price.decimalValue(), null, inStock));
    }

    private JsonNode findProductJsonLd(Document page) {
        for (Element script : page.select(JSON_LD_SELECTOR)) {
            JsonNode product = findProductNode(script.data());
            if (product != null) {
                return product;
            }
        }
        return null;
    }

    private JsonNode findProductNode(String jsonLd) {
        try {
            JsonNode root = objectMapper.readTree(jsonLd);
            if (isProduct(root)) {
                return root;
            }
            for (JsonNode node : root.path("@graph")) {
                if (isProduct(node)) {
                    return node;
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private boolean isProduct(JsonNode node) {
        return "Product".equals(node.path("@type").asText(""));
    }

    private String extractTitle(Document page, JsonNode productJsonLd) {
        if (productJsonLd != null && productJsonLd.hasNonNull("name")) {
            return productJsonLd.get("name").asText();
        }
        return page.title();
    }
}
