package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.backoffice.application.CatalogFigures.ProductOf;
import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CatalogFigures} (UC-33): low stock counts only the active variants of published products, at or
 * under the threshold, sold-out first; a variant names its product for the best sellers.
 */
public abstract class CatalogFiguresContractTest<T extends CatalogFigures> extends ContractTest<T> {

    /** A product named {@code name} (its slug is the name in lowercase), published or hidden; answers its id. */
    protected abstract UUID givenProduct(String name, boolean published);

    /** A variant of {@code product} in size {@code size} (at {@code position}) with {@code available} units. */
    protected abstract UUID givenVariant(UUID product, String size, int position, boolean active, int available);

    @Test
    void givenVariantsWith0_2_3And4Units_whenTheThresholdIs3_thenThreeAreLowSoldOutFirstAndOneIsSoldOut() {
        UUID luna = givenProduct("Luna", true);
        givenVariant(luna, "S", 0, true, 2);
        givenVariant(luna, "M", 1, true, 3);
        givenVariant(luna, "L", 2, true, 4);
        UUID sol = givenProduct("Sol", true);
        givenVariant(sol, "M", 0, true, 0);
        persistAndForget();

        assertThat(subject.lowStock(3, 10)).extracting(LowStockVariant::productName, LowStockVariant::size,
                LowStockVariant::available)
            .containsExactly(tuple("Sol", "M", 0L), tuple("Luna", "S", 2L), tuple("Luna", "M", 3L));
        assertThat(subject.lowStockCount(3)).isEqualTo(3);
        assertThat(subject.lowStockCount(0)).isEqualTo(1);
        assertThat(subject.lowStock(3, 2)).hasSize(2);
    }

    @Test
    void givenAHiddenProductWithNothingAndAnInactiveVariant_whenLowStockIsAsked_thenNeitherCounts() {
        givenVariant(givenProduct("Oculto", false), "M", 0, true, 0);
        givenVariant(givenProduct("Inactivo", true), "M", 0, false, 0);
        persistAndForget();

        assertThat(subject.lowStock(3, 10)).isEmpty();
        assertThat(subject.lowStockCount(3)).isZero();
    }

    @Test
    void givenPublishedAndHiddenProducts_whenCounted_thenOnlyThePublishedCount() {
        givenProduct("Uno", true);
        givenProduct("Dos", true);
        givenProduct("Oculto", false);
        persistAndForget();

        assertThat(subject.publishedProducts()).isEqualTo(2);
    }

    @Test
    void givenVariantsOfTwoProducts_whenTheirProductsAreAsked_thenEachNamesItsOwnAndUnknownOnesAreLeftOut() {
        UUID luna = givenProduct("Luna", true);
        UUID small = givenVariant(luna, "S", 0, true, 5);
        UUID medium = givenVariant(luna, "M", 1, true, 5);
        UUID other = givenVariant(givenProduct("Sol", false), "M", 0, true, 5);
        persistAndForget();

        assertThat(subject.productsOf(List.of(small, medium, other, UUID.randomUUID()))).isEqualTo(Map.of(
            small, new ProductOf("luna", "Luna"),
            medium, new ProductOf("luna", "Luna"),
            other, new ProductOf("sol", "Sol")));
        assertThat(subject.productsOf(List.of())).isEmpty();
    }
}
