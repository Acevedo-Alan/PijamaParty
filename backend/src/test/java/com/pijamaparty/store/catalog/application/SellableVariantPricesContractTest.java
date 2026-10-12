package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static com.pijamaparty.store.catalog.application.ListingFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contract of {@link SellableVariantPrices} (UC-08). */
public abstract class SellableVariantPricesContractTest<T extends SellableVariantPrices>
    extends ListedProductsContractTest<T> {

    private static final List<String> M = List.of("M");
    private static final List<String> ROSA = List.of("Rosa");

    private CategoryId summer;

    @BeforeEach
    void createCategory() {
        summer = category("Verano", true).id();
    }

    @Test
    void givenActivePricedVariantsOfAPublishedProduct_whenAskingTheirPrices_thenEachHasItsOwn() {
        Product product = stored(product(summer, "Pijama", 0, List.of("S", "M"), ROSA, 5000, p -> p
            .price("M", "Rosa", 4500)));
        VariantId small = variant(product, "S", "Rosa");
        VariantId medium = variant(product, "M", "Rosa");
        persistAndForget();

        assertThat(subject.pricesOf(List.of(small, medium)))
            .isEqualTo(Map.of(small, Money.of(5000), medium, Money.of(4500)));
    }

    @Test
    void givenVariantsTheShopDoesNotSell_whenAskingTheirPrices_thenTheyAreLeftOut() {
        Product product = stored(product(summer, "Pijama", 0, List.of("S", "M", "L"), ROSA, 5000, p -> p
            .inactive("M", "Rosa")
            .unpriced("L", "Rosa")));
        Product hidden = stored(product(summer, "Oculto", 1, M, ROSA, 5000, p -> p.hidden()));
        Product ofInactiveCategory = stored(product(category("Invierno", false).id(), "De invierno", 2, M, ROSA, 5000));
        persistAndForget();

        assertThat(subject.pricesOf(List.of(variant(product, "S", "Rosa"), variant(product, "M", "Rosa"),
                variant(product, "L", "Rosa"), variant(hidden, "M", "Rosa"), variant(ofInactiveCategory, "M", "Rosa"),
                VariantId.of(UUID.randomUUID()))))
            .containsOnlyKeys(variant(product, "S", "Rosa"));
    }

    @Test
    void givenNoVariants_whenAskingTheirPrices_thenThereAreNone() {
        assertThat(subject.pricesOf(List.of())).isEmpty();
    }
}
