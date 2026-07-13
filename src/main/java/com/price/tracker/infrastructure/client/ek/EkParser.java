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
import java.util.Objects;
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
    private static final String PRICES_URL_TEMPLATE = "https://ek.ua/ua/ek-item.php?resolved_name_=%s&view_=prices&order_=price";
    // The shop link is only present inside the onmouseover JS of the offer row
    private static final Pattern SHOP_REDIRECT_URL = Pattern.compile("https://ek\\.ua/[^\"]*clcp\\.php[^\"]*");
    // Regular products use td.where-buy-price rows, laptop-like configurations td.conf-prices-price-big
    private static final String SHOP_ROW_PRICE_SELECTOR = "td.where-buy-price, td.conf-prices-price-big";
    private static final String SHOP_NAME_SELECTOR = "a.it-shop";
    private static final String MODIFICATION_GROUP_SELECTOR = "div.m-c-f1-pl--inline";
    private static final String COLOR_SWATCH_SELECTOR = "div.c-d[title]";
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

    private record MarketView(BigDecimal lowPrice, boolean inStock, ProductSnapshot.AggregateStats stats) {
    }

    ProductSnapshot parse(Document productPage, List<Document> pricesPages, String productUrl, String colorId) {
        JsonNode productJsonLd = jsonLdProductExtractor.findProduct(productPage);
        if (productJsonLd == null) {
            throw new ProductParsingException("No JSON-LD product found: " + productUrl);
        }
        JsonNode aggregateOffer = productJsonLd.path("offers");
        List<SizeOffer> shopOffers = shopOffers(pricesPages);
        List<ProductSnapshot.VariantGroup> variants = variantGroups(productPage, productUrl, colorId);
        MarketView market = colorId == null
                ? wholeModelMarket(aggregateOffer, productUrl)
                : colorFilteredMarket(shopOffers, aggregateOffer, productUrl);
        String title = productJsonLd.path("name").asString(productPage.title());
        if (colorId != null) {
            title = title + " · " + selectedColorLabel(variants, colorId);
        }
        List<SizeOffer> offers = new ArrayList<>();
        offers.add(new SizeOffer(SizeOffer.MIN_PRICE_LABEL, market.lowPrice(), null, market.inStock()));
        offers.addAll(shopOffers);
        return new ProductSnapshot(title, market.lowPrice(), null, market.inStock(), offers, market.stats(), variants);
    }

    private MarketView wholeModelMarket(JsonNode aggregateOffer, String productUrl) {
        BigDecimal lowPrice = requiredPrice(aggregateOffer, "lowPrice", productUrl);
        boolean inStock = aggregateOffer.path("availability").asString("").endsWith("InStock");
        return new MarketView(lowPrice, inStock, new ProductSnapshot.AggregateStats(
                lowPrice,
                optionalPrice(aggregateOffer, "price"),
                optionalPrice(aggregateOffer, "highPrice"),
                aggregateOffer.path("offerCount").asInt(0)));
    }

    // JSON-LD covers the whole model, so color-filtered numbers come from the offer rows
    private MarketView colorFilteredMarket(List<SizeOffer> shopOffers, JsonNode aggregateOffer, String productUrl) {
        if (shopOffers.isEmpty()) {
            BigDecimal lowPrice = requiredPrice(aggregateOffer, "lowPrice", productUrl);
            return new MarketView(lowPrice, false, new ProductSnapshot.AggregateStats(lowPrice, null, null, 0));
        }
        BigDecimal lowPrice = shopOffers.getFirst().price();
        return new MarketView(lowPrice, true, new ProductSnapshot.AggregateStats(
                lowPrice, null, shopOffers.getLast().price(), shopOffers.size()));
    }

    private List<ProductSnapshot.VariantGroup> variantGroups(Document productPage, String productUrl, String colorId) {
        List<ProductSnapshot.VariantGroup> groups = new ArrayList<>();
        List<ProductSnapshot.VariantOption> colors = colorOptions(productPage, productUrl, colorId);
        if (colors.size() > 1) {
            colors.addFirst(new ProductSnapshot.VariantOption(
                    ProductSnapshot.VariantOption.ANY_LABEL, productUrl, colorId == null));
            groups.add(new ProductSnapshot.VariantGroup("color", colors));
        }
        for (Element modificationBlock : productPage.select(MODIFICATION_GROUP_SELECTOR)) {
            List<ProductSnapshot.VariantOption> options = modificationOptions(modificationBlock);
            if (options.size() > 1) {
                groups.add(new ProductSnapshot.VariantGroup("modification", options));
            }
        }
        return groups;
    }

    private List<ProductSnapshot.VariantOption> colorOptions(Document productPage, String productUrl, String colorId) {
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
        return colors;
    }

    private List<ProductSnapshot.VariantOption> modificationOptions(Element modificationBlock) {
        List<ProductSnapshot.VariantOption> options = new ArrayList<>();
        for (Element option : modificationBlock.select("> span")) {
            Element link = option.selectFirst("a");
            options.add(new ProductSnapshot.VariantOption(
                    option.text().trim(),
                    link != null ? link.absUrl("href") : null,
                    option.hasClass("current")));
        }
        return options;
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
        pricesPages.stream()
                .flatMap(pricesPage -> pricesPage.select(SHOP_ROW_PRICE_SELECTOR).stream())
                .map(this::shopOffer)
                .filter(Objects::nonNull)
                // rows are price-sorted, so the first offer per shop is its cheapest
                .forEach(offer -> offersByShop.putIfAbsent(offer.label(), offer));
        return offersByShop.values().stream().limit(MAX_SHOP_OFFERS).toList();
    }

    private SizeOffer shopOffer(Element priceCell) {
        Element row = priceCell.parent();
        Element shopLink = row != null ? row.selectFirst(SHOP_NAME_SELECTOR) : null;
        BigDecimal price = priceFrom(priceCell);
        if (shopLink == null || price == null) {
            return null;
        }
        return new SizeOffer(shopLink.text().trim(), price, null, true, redirectUrl(priceCell));
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
            return new BigDecimal(value.asString(""));
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
