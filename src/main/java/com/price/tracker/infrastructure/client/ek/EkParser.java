package com.price.tracker.infrastructure.client.ek;

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
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EK (e-katalog) is a price aggregator: one "size" here is one shop offer,
 * plus a virtual MIN_PRICE offer tracking the lowest price across all shops.
 * Model variants become wizard variant groups: modifications (41mm/45mm) are
 * sibling model pages, colors filter the offer list of the same model — the
 * chosen color is kept in the tracked url as a ?colors_=N query.
 */
@Component
@RequiredArgsConstructor
public class EkParser implements SiteParser {

    private static final Pattern MODEL_SLUG = Pattern.compile("/([^/]+)\\.htm");
    private static final Pattern COLOR_ID_IN_URL = Pattern.compile("[?&]colors_=(\\d+)");
    private static final Pattern COLOR_ID_IN_JSOURCE = Pattern.compile("colors_=(\\d+)");
    // order_=price sorts offers cheapest-first, so the top of page 1 is what we want
    private static final String PRICES_URL_TEMPLATE = "https://ek.ua/ua/ek-item.php?resolved_name_=%s&view_=prices&order_=price";
    // The shop link is only present inside the onmouseover JS of the offer row
    private static final Pattern SHOP_REDIRECT_URL = Pattern.compile("https://ek\\.ua/[^\"]*clcp\\.php[^\"]*");
    private static final String SHOP_ROW_PRICE_SELECTOR = "td.where-buy-price";
    private static final String SHOP_NAME_SELECTOR = "a.it-shop";
    private static final String MODIFICATION_GROUP_SELECTOR = "div.m-c-f1-pl--inline";
    private static final String COLOR_SWATCH_SELECTOR = "div.c-d[title]";
    // 20 offers per prices page; stop paging defensively even if the pager lies
    private static final int MAX_PRICE_PAGES = 10;
    private static final int MAX_SHOP_OFFERS = 150;

    private final TrackerProperties trackerProperties;
    private final JsonLdProductExtractor jsonLdProductExtractor;

    @Override
    public Site site() {
        return Site.EK;
    }

    @Override
    public ProductSnapshot fetch(String url) {
        String colorId = colorIdFrom(url);
        String productUrl = stripQuery(url);
        Document productPage = get(productUrl);
        return parse(productPage, fetchPricesPages(productUrl, colorId), productUrl, colorId);
    }

    // Prices are paginated by 20; walk the pages so every shop is selectable
    private List<Document> fetchPricesPages(String productUrl, String colorId) {
        String baseUrl = PRICES_URL_TEMPLATE.formatted(modelSlug(productUrl));
        if (colorId != null) {
            baseUrl += "&colors_=" + colorId;
        }
        List<Document> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < MAX_PRICE_PAGES; pageIndex++) {
            Document page = get(pageIndex == 0 ? baseUrl : baseUrl + "&page_=" + pageIndex);
            if (page.select(SHOP_ROW_PRICE_SELECTOR).isEmpty()) {
                break;
            }
            pages.add(page);
        }
        return pages;
    }

    ProductSnapshot parse(Document productPage, List<Document> pricesPages, String productUrl, String colorId) {
        JsonNode productJsonLd = jsonLdProductExtractor.findProduct(productPage);
        if (productJsonLd == null) {
            throw new ProductParsingException("No JSON-LD product found: " + productUrl);
        }
        String title = productJsonLd.path("name").asText(productPage.title());
        JsonNode aggregateOffer = productJsonLd.path("offers");
        List<SizeOffer> shopOffers = shopOffers(pricesPages);
        List<ProductSnapshot.VariantGroup> variants = variantGroups(productPage, productUrl, colorId);

        BigDecimal lowPrice;
        boolean inStock;
        ProductSnapshot.AggregateStats stats;
        if (colorId == null) {
            lowPrice = requiredPrice(aggregateOffer, "lowPrice", productUrl);
            inStock = aggregateOffer.path("availability").asText("").endsWith("InStock");
            stats = new ProductSnapshot.AggregateStats(
                    lowPrice,
                    optionalPrice(aggregateOffer, "price"),
                    optionalPrice(aggregateOffer, "highPrice"),
                    aggregateOffer.path("offerCount").asInt(0));
        } else {
            // JSON-LD covers the whole model, so color-filtered numbers come from the offer rows
            inStock = !shopOffers.isEmpty();
            lowPrice = inStock ? shopOffers.getFirst().price() : requiredPrice(aggregateOffer, "lowPrice", productUrl);
            stats = new ProductSnapshot.AggregateStats(
                    lowPrice,
                    null,
                    inStock ? shopOffers.getLast().price() : null,
                    shopOffers.size());
            title = title + " · " + selectedColorLabel(variants, colorId);
        }

        List<SizeOffer> offers = new ArrayList<>();
        offers.add(new SizeOffer(SizeOffer.MIN_PRICE_LABEL, lowPrice, null, inStock));
        offers.addAll(shopOffers);
        return new ProductSnapshot(title, lowPrice, null, inStock, offers, stats, variants);
    }

    private List<ProductSnapshot.VariantGroup> variantGroups(Document productPage, String productUrl, String colorId) {
        List<ProductSnapshot.VariantGroup> groups = new ArrayList<>();
        List<ProductSnapshot.VariantOption> colors = new ArrayList<>();
        for (Element swatch : productPage.select(COLOR_SWATCH_SELECTOR)) {
            Matcher matcher = COLOR_ID_IN_JSOURCE.matcher(swatch.attr("jsource"));
            if (!matcher.find()) {
                continue;
            }
            String swatchColorId = matcher.group(1);
            colors.add(new ProductSnapshot.VariantOption(
                    swatch.attr("title").trim(),
                    productUrl + "?colors_=" + swatchColorId,
                    swatchColorId.equals(colorId)));
        }
        if (colors.size() > 1) {
            colors.addFirst(new ProductSnapshot.VariantOption(
                    ProductSnapshot.VariantOption.ANY_LABEL, productUrl, colorId == null));
            groups.add(new ProductSnapshot.VariantGroup("color", colors));
        }
        for (Element modificationBlock : productPage.select(MODIFICATION_GROUP_SELECTOR)) {
            List<ProductSnapshot.VariantOption> options = new ArrayList<>();
            for (Element option : modificationBlock.select("> span")) {
                Element link = option.selectFirst("a");
                options.add(new ProductSnapshot.VariantOption(
                        option.text().trim(),
                        link != null ? link.absUrl("href") : null,
                        option.hasClass("current")));
            }
            if (options.size() > 1) {
                groups.add(new ProductSnapshot.VariantGroup("modification", options));
            }
        }
        return groups;
    }

    private String selectedColorLabel(List<ProductSnapshot.VariantGroup> variants, String colorId) {
        return variants.stream()
                .flatMap(group -> group.options().stream())
                .filter(ProductSnapshot.VariantOption::selected)
                .map(ProductSnapshot.VariantOption::label)
                .findFirst()
                .orElse("colors_=" + colorId);
    }

    private List<SizeOffer> shopOffers(List<Document> pricesPages) {
        Map<String, SizeOffer> offersByShop = new LinkedHashMap<>();
        for (Document pricesPage : pricesPages) {
            for (Element priceCell : pricesPage.select(SHOP_ROW_PRICE_SELECTOR)) {
                Element row = priceCell.parent();
                if (row == null) {
                    continue;
                }
                Element shopLink = row.selectFirst(SHOP_NAME_SELECTOR);
                BigDecimal price = priceFrom(priceCell);
                if (shopLink == null || price == null) {
                    continue;
                }
                String shopName = shopLink.text().trim();
                // Rows are sorted by price, so the first offer per shop is its cheapest one
                offersByShop.putIfAbsent(shopName, new SizeOffer(shopName, price, null, true, redirectUrl(priceCell)));
                if (offersByShop.size() >= MAX_SHOP_OFFERS) {
                    return new ArrayList<>(offersByShop.values());
                }
            }
        }
        return new ArrayList<>(offersByShop.values());
    }

    private BigDecimal priceFrom(Element priceCell) {
        Element priceLink = priceCell.selectFirst("a");
        if (priceLink == null) {
            return null;
        }
        String digits = priceLink.text().replaceAll("\\D", "");
        return digits.isEmpty() ? null : new BigDecimal(digits);
    }

    private String redirectUrl(Element priceCell) {
        Element priceLink = priceCell.selectFirst("a");
        if (priceLink == null) {
            return null;
        }
        Matcher matcher = SHOP_REDIRECT_URL.matcher(priceLink.attr("onmouseover"));
        return matcher.find() ? matcher.group() : null;
    }

    private String modelSlug(String productUrl) {
        Matcher matcher = MODEL_SLUG.matcher(productUrl);
        String slug = null;
        while (matcher.find()) {
            slug = matcher.group(1);
        }
        if (slug == null) {
            throw new ProductParsingException("Not an EK product page: " + productUrl);
        }
        return slug;
    }

    private String colorIdFrom(String url) {
        Matcher matcher = COLOR_ID_IN_URL.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String stripQuery(String url) {
        int queryStart = url.indexOf('?');
        return queryStart < 0 ? url : url.substring(0, queryStart);
    }

    private BigDecimal requiredPrice(JsonNode aggregateOffer, String field, String url) {
        BigDecimal price = optionalPrice(aggregateOffer, field);
        if (price == null) {
            throw new ProductParsingException("EK JSON-LD offer has no numeric " + field + ": " + url);
        }
        return price;
    }

    private BigDecimal optionalPrice(JsonNode aggregateOffer, String field) {
        JsonNode value = aggregateOffer.path(field);
        if (value.isNumber()) {
            return value.decimalValue();
        }
        try {
            return new BigDecimal(value.asText(""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Document get(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(trackerProperties.http().userAgent())
                    .timeout(trackerProperties.http().timeoutMs())
                    .get();
        } catch (IOException e) {
            throw new ProductParsingException("Failed to load page: " + url, e);
        }
    }
}
