package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.CategoryId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contract of {@link ListedProductSlugs} (T-E6-23). */
public abstract class ListedProductSlugsContractTest<T extends ListedProductSlugs>
    extends ListedProductsContractTest<T> {

    private static final List<String> M = List.of("M");
    private static final List<String> ROSA = List.of("Rosa");

    private CategoryId summer;

    @BeforeEach
    void createCategory() {
        summer = category("Verano", true).id();
    }

    @Test
    void givenListedProducts_whenAskingTheirSlugs_thenTheyComeNewestFirstWithStockOrNot() {
        stocked(product(summer, "Pijama viejo", 0, M, ROSA, 1000), 0);
        stocked(product(summer, "Pijama nuevo", 2, M, ROSA, 1000), 3);
        stored(product(summer, "Camison", 1, M, ROSA, 1000));
        persistAndForget();

        assertThat(subject.newestFirst(10)).containsExactly("pijama-nuevo", "camison", "pijama-viejo");
        assertThat(subject.newestFirst(2)).containsExactly("pijama-nuevo", "camison");
    }

    @Test
    void givenProductsVisitorsCannotSee_whenAskingTheSlugs_thenTheyAreLeftOut() {
        stored(product(summer, "Pijama", 0, M, ROSA, 1000));
        stored(product(summer, "Oculto", 1, M, ROSA, 1000, p -> p.hidden()));
        stored(product(summer, "Sin precio", 2, M, ROSA, 1000, p -> p.unpriced("M", "Rosa")));
        stored(product(summer, "Inactivo", 3, M, ROSA, 1000, p -> p.inactive("M", "Rosa")));
        stored(product(category("Invierno", false).id(), "De invierno", 4, M, ROSA, 1000));
        persistAndForget();

        assertThat(subject.newestFirst(10)).containsExactly("pijama");
    }
}
