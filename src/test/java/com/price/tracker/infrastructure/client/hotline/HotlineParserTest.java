package com.price.tracker.infrastructure.client.hotline;

import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.infrastructure.client.jsonld.JsonLdProductExtractor;
import com.price.tracker.testsupport.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HotlineParserTest {

    private final HotlineParser parser = new HotlineParser(null, new JsonLdProductExtractor(), new HotlineStateExtractor());

    @Test
    void when_productPage_parsesTitleAndAggregateStats() {
        ProductSnapshot product = parser.parse(
                Fixtures.page("hotline-product.html", "https://hotline.ua/"), "https://hotline.ua/test");

        assertThat(product.title()).isEqualTo("Logitech G Pro X Superlight 2 Lightspeed Wireless Black (910-006630)");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("4479"));
        assertThat(product.stats().maxPrice()).isEqualByComparingTo(new BigDecimal("7499"));
        assertThat(product.stats().offerCount()).isEqualTo(90);
    }

    @Test
    void when_offersInNuxtState_extractsDedupedShopsSortedByPrice() {
        ProductSnapshot product = parser.parse(
                Fixtures.page("hotline-product.html", "https://hotline.ua/"), "https://hotline.ua/test");

        // 90 state offers minus non-new conditions and duplicate shops, plus MIN_PRICE
        assertThat(product.sizes()).hasSize(83);
        assertThat(product.sizes().getFirst().label()).isEqualTo(SizeOffer.MIN_PRICE_LABEL);
        SizeOffer cheapestShop = product.sizes().get(1);
        assertThat(cheapestShop.label()).isEqualTo("DIGITALSHOP");
        assertThat(cheapestShop.price()).isEqualByComparingTo(new BigDecimal("4479"));
        assertThat(cheapestShop.url()).startsWith("https://hotline.ua/go/price/");
    }
}
