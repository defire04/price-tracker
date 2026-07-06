package com.price.tracker.infrastructure.client.jsonld;

import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.exception.ProductParsingException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Shared fallback source: schema.org Product from JSON-LD script tags.
 * Gives title and a single price, but no per-size stock — availability there is unreliable.
 */
@Component
public class JsonLdProductExtractor {

    private static final String JSON_LD_SELECTOR = "script[type=application/ld+json]";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode findProduct(Document page) {
        for (Element script : page.select(JSON_LD_SELECTOR)) {
            JsonNode product = findProductNode(script.data());
            if (product != null) {
                return product;
            }
        }
        return null;
    }

    public List<SizeOffer> oneSizeOffers(JsonNode productJsonLd, String url) {
        JsonNode offer = productJsonLd.path("offers");
        JsonNode price = offer.path("price");
        if (!price.isNumber()) {
            throw new ProductParsingException("JSON-LD offer has no numeric price: " + url);
        }
        boolean inStock = offer.path("availability").asString("").endsWith("InStock");
        return List.of(new SizeOffer(SizeOffer.ONE_SIZE_LABEL, price.decimalValue(), null, inStock));
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
        return "Product".equals(node.path("@type").asString(""));
    }
}
