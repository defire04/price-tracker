package com.price.tracker.infrastructure.client.intertop;

import com.price.tracker.domain.model.SizeOffer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sizes with per-size stock live in the Nuxt devalue payload: a flat JSON array
 * where object field values are indexes pointing back into the same array.
 */
@Component
public class NuxtDataExtractor {

    private static final Set<String> SIZE_OBJECT_KEYS = Set.of("sku", "label", "price", "availability");
    private static final String AVAILABLE_ONLINE_VALUE = "online";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SizeOffer> extractSizeOffers(String nuxtDataJson) {
        JsonNode flatArray = objectMapper.readTree(nuxtDataJson);
        if (!flatArray.isArray()) {
            return List.of();
        }
        Map<String, SizeOffer> offersByLabel = new LinkedHashMap<>();
        for (JsonNode node : flatArray) {
            if (isSizeObject(node)) {
                SizeOffer offer = toSizeOffer(node, flatArray);
                offersByLabel.putIfAbsent(offer.label(), offer);
            }
        }
        return List.copyOf(offersByLabel.values());
    }

    private boolean isSizeObject(JsonNode node) {
        return node.isObject() && SIZE_OBJECT_KEYS.stream().allMatch(node::has);
    }

    private SizeOffer toSizeOffer(JsonNode sizeObject, JsonNode flatArray) {
        String label = resolve(sizeObject.get("label"), flatArray).asText();
        if (label.isBlank()) {
            label = SizeOffer.ONE_SIZE_LABEL;
        }
        String availability = resolve(sizeObject.get("availability"), flatArray).asText();
        JsonNode quantityLimit = resolve(sizeObject.get("quantityLimit"), flatArray);
        boolean inStock = AVAILABLE_ONLINE_VALUE.equals(availability)
                && quantityLimit != null && quantityLimit.asInt(0) > 0;
        JsonNode priceObject = resolve(sizeObject.get("price"), flatArray);
        BigDecimal price = extractAmount(priceObject.get("value"), flatArray);
        BigDecimal regularPrice = extractAmount(priceObject.get("regularPrice"), flatArray);
        if (price != null && price.compareTo(regularPrice == null ? BigDecimal.ZERO : regularPrice) == 0) {
            regularPrice = null;
        }
        return new SizeOffer(label, price, regularPrice, inStock);
    }

    private BigDecimal extractAmount(JsonNode moneyReference, JsonNode flatArray) {
        JsonNode moneyObject = resolve(moneyReference, flatArray);
        if (moneyObject == null || !moneyObject.isObject()) {
            return null;
        }
        JsonNode amount = resolve(moneyObject.get("amount"), flatArray);
        return amount != null && amount.isNumber() ? amount.decimalValue() : null;
    }

    private JsonNode resolve(JsonNode indexReference, JsonNode flatArray) {
        if (indexReference == null || !indexReference.isInt()) {
            return indexReference;
        }
        int index = indexReference.asInt();
        return index >= 0 && index < flatArray.size() ? flatArray.get(index) : null;
    }
}
