package com.price.tracker.infrastructure.client.intertop;

import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.infrastructure.client.jsonld.JsonLdProductExtractor;
import com.price.tracker.testsupport.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class IntertopParserTest {

    private final IntertopParser parser = new IntertopParser(null, new NuxtDataExtractor(), new JsonLdProductExtractor());

    @Test
    void when_productPage_parsesTitleAndPrices() {
        ProductSnapshot product = parser.parse(
                Fixtures.page("intertop-product.html", "https://intertop.ua/"), "https://intertop.ua/test");

        assertThat(product.title()).isEqualTo("Кросівки Respect модель IK83-149439");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("1377"));
        assertThat(product.regularPrice()).isEqualByComparingTo(new BigDecimal("4595"));
    }

    @Test
    void when_allSizesSoldOut_reportsOutOfStock() {
        ProductSnapshot product = parser.parse(
                Fixtures.page("intertop-product.html", "https://intertop.ua/"), "https://intertop.ua/test");

        assertThat(product.inStock()).isFalse();
        assertThat(product.sizes()).hasSize(6)
                .extracting(SizeOffer::label)
                .containsExactly("39", "40", "41", "42", "43", "44");
        assertThat(product.sizes()).noneMatch(SizeOffer::inStock);
    }

    @Test
    void when_pageLoadedThroughCloudflareInBrowser_parsesSizes() {
        ProductSnapshot product = parser.parse(
                Fixtures.page("intertop-product-cf.html", "https://intertop.ua/"), "https://intertop.ua/test");

        assertThat(product.title()).isEqualTo("Штани Marc O’Polo модель 523134010134-898");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("3399"));
        assertThat(product.regularPrice()).isEqualByComparingTo(new BigDecimal("6799"));
        assertThat(product.inStock()).isTrue();
        assertThat(product.sizes()).hasSize(13);
        assertThat(product.findSize("32/34")).get().extracting(SizeOffer::inStock).isEqualTo(false);
    }
}
