package com.price.tracker.infrastructure.client.ek;

import com.price.tracker.domain.model.ProductSnapshot;
import com.price.tracker.domain.model.SizeOffer;
import com.price.tracker.infrastructure.client.jsonld.JsonLdProductExtractor;
import com.price.tracker.testsupport.Fixtures;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EkParserTest {

    private static final String PRODUCT_URL = "https://ek.ua/ua/GARMIN-VENU-4-45MM.htm";

    private final EkParser parser = new EkParser(null, new JsonLdProductExtractor());

    private Document productPage() {
        return Fixtures.page("ek-product.html", "https://ek.ua/");
    }

    @Test
    void when_noColorFilter_takesStatsFromJsonLd() {
        ProductSnapshot product = parser.parse(productPage(),
                List.of(Fixtures.page("ek-prices.html", "https://ek.ua/")), PRODUCT_URL, null);

        assertThat(product.title()).isEqualTo("Garmin Venu 4  45mm");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("22853"));
        assertThat(product.stats().avgPrice()).isEqualByComparingTo(new BigDecimal("28940"));
        assertThat(product.stats().maxPrice()).isEqualByComparingTo(new BigDecimal("39574"));
        assertThat(product.stats().offerCount()).isEqualTo(230);
    }

    @Test
    void when_pricesPageParsed_buildsMinPriceAndShopOffers() {
        ProductSnapshot product = parser.parse(productPage(),
                List.of(Fixtures.page("ek-prices.html", "https://ek.ua/")), PRODUCT_URL, null);

        assertThat(product.sizes()).hasSize(13);
        assertThat(product.sizes().getFirst().label()).isEqualTo(SizeOffer.MIN_PRICE_LABEL);
        SizeOffer cheapestShop = product.sizes().get(1);
        assertThat(cheapestShop.label()).isEqualTo("Denika.ua");
        assertThat(cheapestShop.price()).isEqualByComparingTo(new BigDecimal("22853"));
        assertThat(cheapestShop.url()).contains("clcp.php");
    }

    @Test
    void when_colorFilterActive_takesStatsFromFilteredOffers() {
        ProductSnapshot product = parser.parse(productPage(),
                List.of(Fixtures.page("ek-prices-black.html", "https://ek.ua/")), PRODUCT_URL, "23");

        assertThat(product.title()).isEqualTo("Garmin Venu 4  45mm · чорний");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("24980"));
        assertThat(product.stats().maxPrice()).isEqualByComparingTo(new BigDecimal("31157"));
        assertThat(product.stats().offerCount()).isEqualTo(2);
        assertThat(product.sizes()).hasSize(3);
    }

    @Test
    void when_pageHasSwatchesAndModifications_buildsVariantGroups() {
        ProductSnapshot product = parser.parse(productPage(),
                List.of(Fixtures.page("ek-prices.html", "https://ek.ua/")), PRODUCT_URL, null);

        assertThat(product.variants()).hasSize(2);
        ProductSnapshot.VariantGroup colors = product.variants().getFirst();
        assertThat(colors.options().getFirst().label()).isEqualTo(ProductSnapshot.VariantOption.ANY_LABEL);
        assertThat(colors.options().getFirst().selected()).isTrue();
        ProductSnapshot.VariantGroup modifications = product.variants().getLast();
        assertThat(modifications.options())
                .extracting(ProductSnapshot.VariantOption::label, ProductSnapshot.VariantOption::selected)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("41mm", false),
                        org.assertj.core.groups.Tuple.tuple("45mm", true));
    }
}
