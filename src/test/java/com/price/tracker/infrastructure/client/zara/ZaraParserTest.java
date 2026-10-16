package com.price.tracker.infrastructure.client.zara;

import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.testsupport.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ZaraParserTest {

    private static final String PRODUCT_URL = "https://www.zara.com/ua/uk/jeans-p08062430.html";

    private final ZaraParser parser = new ZaraParser(null);

    private ProductSnapshot parse(String url) {
        return parser.parse(Fixtures.page("zara-product.html", "https://www.zara.com/"), url);
    }

    @Test
    void when_colorInUrl_parsesSizesOfThatColor() {
        ProductSnapshot product = parse(PRODUCT_URL + "?v1=545461865");

        assertThat(product.title()).isEqualTo("ДЖИНСИ STRAIGHT FIT · Світло-синій");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("1599"));
        assertThat(product.regularPrice()).isNull();
        assertThat(product.inStock()).isTrue();
        assertThat(product.sizes())
                .extracting(SizeOffer::label)
                .containsExactly("36", "38", "40", "42", "44", "46", "48");
        assertThat(product.sizes())
                .filteredOn(SizeOffer::inStock)
                .extracting(SizeOffer::label)
                .containsExactly("36", "38", "40", "42");
    }

    @Test
    void when_otherColorInUrl_parsesItsOwnStock() {
        ProductSnapshot product = parse(PRODUCT_URL + "?v1=545461866");

        assertThat(product.title()).isEqualTo("ДЖИНСИ STRAIGHT FIT · Білий");
        assertThat(product.sizes()).hasSize(7).allMatch(SizeOffer::inStock);
    }

    @Test
    void when_noColorInUrl_takesFirstColorLikeThePage() {
        // the page lists the color it shows first
        ProductSnapshot product = parse(PRODUCT_URL);

        assertThat(product.title()).isEqualTo("ДЖИНСИ STRAIGHT FIT · Світло-синій");
    }

    @Test
    void when_severalColors_offersThemAsVariantsWithV1Urls() {
        ProductSnapshot product = parse(PRODUCT_URL + "?v1=545461865");

        assertThat(product.variants()).hasSize(1);
        ProductSnapshot.VariantGroup colors = product.variants().getFirst();
        assertThat(colors.options()).extracting(ProductSnapshot.VariantOption::label)
                .containsExactly("Світло-синій", "Вугільний", "Білий");
        assertThat(colors.options()).filteredOn(ProductSnapshot.VariantOption::selected)
                .extracting(ProductSnapshot.VariantOption::url)
                .containsExactly(PRODUCT_URL + "?v1=545461865");
    }
}
