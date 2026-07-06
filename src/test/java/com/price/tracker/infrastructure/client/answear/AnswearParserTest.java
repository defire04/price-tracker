package com.price.tracker.infrastructure.client.answear;

import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.infrastructure.client.jsonld.JsonLdProductExtractor;
import com.price.tracker.testsupport.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AnswearParserTest {

    private final AnswearParser parser = new AnswearParser(null, new JsonLdProductExtractor());

    @Test
    void when_discountedProduct_parsesPriceAndRegularPrice() {
        ProductSnapshot product = parser.parse(
                Fixtures.page("answear-product.html", "https://answear.ua/"), "https://answear.ua/test");

        assertThat(product.title()).isEqualTo("Хлопковое поло HUGO Dereso222");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("2899"));
        assertThat(product.regularPrice()).isEqualByComparingTo(new BigDecimal("5199"));
    }

    @Test
    void when_productPage_parsesSizesFromReactQueryState() {
        ProductSnapshot product = parser.parse(
                Fixtures.page("answear-product.html", "https://answear.ua/"), "https://answear.ua/test");

        assertThat(product.sizes()).hasSize(5)
                .extracting(SizeOffer::label)
                .containsExactly("S", "M", "L", "XL", "XXL");
        assertThat(product.sizes()).allMatch(SizeOffer::inStock);
    }
}
