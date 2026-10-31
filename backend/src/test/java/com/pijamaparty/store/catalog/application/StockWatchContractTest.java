package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static com.pijamaparty.store.catalog.application.ListingFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link StockWatch} (UC-33): only active variants of published products count as low stock, at or
 * under the threshold of available units, sold-out ones first; a variant without stock has none.
 */
public abstract class StockWatchContractTest<T extends StockWatch> extends ListedProductsContractTest<T> {

    private static final List<String> ROSA = List.of("Rosa");

    private CategoryId summer;

    @BeforeEach
    void createCategory() {
        summer = category("Verano", true).id();
    }

    @Test
    void givenAThresholdOf3_whenVariantsHave2_3And4_thenTheFirstTwoAreLowAndTheSoldOutComesFirst() {
        Product luna = stored(product(summer, "Pijama Luna", 0, List.of("S", "M", "L"), ROSA, 1000));
        givenAvailable(variant(luna, "S", "Rosa"), 2);
        givenAvailable(variant(luna, "M", "Rosa"), 3);
        givenAvailable(variant(luna, "L", "Rosa"), 4);
        Product sol = stored(product(summer, "Camisón Sol", 1, List.of("M"), ROSA, 1000));
        givenAvailable(variant(sol, "M", "Rosa"), 0);
        persistAndForget();

        assertThat(subject.lowStock(3, 10))
            .extracting(LowStockVariant::productName, LowStockVariant::size, LowStockVariant::color,
                LowStockVariant::available)
            .containsExactly(
                tuple("Camisón Sol", "M", "Rosa", 0L),
                tuple("Pijama Luna", "S", "Rosa", 2L),
                tuple("Pijama Luna", "M", "Rosa", 3L));
        assertThat(subject.lowStockCount(3)).isEqualTo(3);
        assertThat(subject.lowStockCount(0)).isEqualTo(1);
    }

    @Test
    void givenAHiddenProductAndAnInactiveVariant_whenLowStockIsAsked_thenNeitherShows() {
        stored(product(summer, "Oculto", 0, List.of("M"), ROSA, 1000, builder -> builder.hidden()));
        stored(product(summer, "Con talle inactivo", 1, List.of("S", "M"), ROSA, 1000,
            builder -> builder.inactive("S", "Rosa")));
        persistAndForget();

        assertThat(subject.lowStock(3, 10)).extracting(LowStockVariant::productName, LowStockVariant::size)
            .containsExactly(tuple("Con talle inactivo", "M"));
        assertThat(subject.lowStockCount(0)).isEqualTo(1);
    }

    @Test
    void givenMoreLowVariantsThanTheLimit_whenListed_thenTheLimitHoldsAndTheCountHasThemAll() {
        stored(product(summer, "Pijama", 0, List.of("S", "M", "L"), List.of("Rosa", "Celeste"), 1000));
        persistAndForget();

        assertThat(subject.lowStock(3, 4)).hasSize(4);
        assertThat(subject.lowStockCount(3)).isEqualTo(6);
    }

    @Test
    void givenPublishedAndHiddenProducts_whenCounted_thenOnlyThePublishedCount() {
        stored(product(summer, "Publicado", 0, List.of("M"), ROSA, 1000));
        stored(product(summer, "Otro publicado", 1, List.of("M"), ROSA, 1000));
        stored(product(summer, "Oculto", 2, List.of("M"), ROSA, 1000, builder -> builder.hidden()));
        persistAndForget();

        assertThat(subject.publishedProducts()).isEqualTo(2);
    }
}
