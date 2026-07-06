package com.price.tracker.infrastructure.client.stradivarius;

import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.infrastructure.client.jsonld.JsonLdProductExtractor;
import com.price.tracker.testsupport.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StradivariusParserTest {

    private final StradivariusParser parser = new StradivariusParser(null, new JsonLdProductExtractor());

    @Test
    void when_productPage_parsesTitleAndJsonLdPrice() {
        ProductSnapshot product = parser.parse(
                Fixtures.page("stradivarius-product.html", "https://www.stradivarius.com/"), "https://www.stradivarius.com/test");

        assertThat(product.title()).isEqualTo("Довга струмлива сукня з відкритою спиною");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("1899"));
        assertThat(product.regularPrice()).isNull();
    }

    @Test
    void when_sizeMarkedNoStock_reportsItOutOfStock() {
        ProductSnapshot product = parser.parse(
                Fixtures.page("stradivarius-product.html", "https://www.stradivarius.com/"), "https://www.stradivarius.com/test");

        assertThat(product.sizes()).hasSize(5)
                .extracting(SizeOffer::label, SizeOffer::inStock)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("XS", true),
                        org.assertj.core.groups.Tuple.tuple("S", true),
                        org.assertj.core.groups.Tuple.tuple("M", true),
                        org.assertj.core.groups.Tuple.tuple("L", true),
                        org.assertj.core.groups.Tuple.tuple("XL", false));
    }
}
