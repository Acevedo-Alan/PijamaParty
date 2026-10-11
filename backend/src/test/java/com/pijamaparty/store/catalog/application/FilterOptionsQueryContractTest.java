package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static com.pijamaparty.store.catalog.application.ListingFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Contract of {@link FilterOptionsQuery} (UC-02). */
public abstract class FilterOptionsQueryContractTest<T extends FilterOptionsQuery>
    extends ListedProductsContractTest<T> {

    @Test
    void givenNoListedProducts_whenAskingForOptions_thenThereAreNone() {
        CategoryId summer = category("Verano", true).id();
        stocked(product(summer, "Oculto", 0, List.of("M"), List.of("Rosa"), 1000, p -> p.hidden()), 1);
        persistAndForget();

        assertThat(subject.options(Optional.empty())).isEqualTo(new FilterOptions(List.of(), List.of(), List.of(),
            List.of(), List.of(), Optional.empty(), Optional.empty()));
    }

    @Test
    void givenListedProducts_whenAskingForOptions_thenSizesAndColorsWithStockComeInDisplayOrder() {
        CategoryId summer = category("Verano", true).id();
        Product first = stored(product(summer, "Primero", 0, List.of("S", "M", "L"), List.of("Rosa", "Celeste"), 1000,
            p -> p.inactive("L", "Celeste")));
        givenAvailable(variant(first, "M", "Rosa"), 1);
        givenAvailable(variant(first, "S", "Celeste"), 2);
        givenAvailable(variant(first, "L", "Celeste"), 3);
        // Ties on the smallest position go by text: S and XL at 0, Blanco and Celeste at 1.
        Product second = stored(product(summer, "Segundo", 1, List.of("XL", "S"), List.of("Negro", "Blanco"), 1000,
            p -> p.unpriced("S", "Negro")));
        givenAvailable(variant(second, "XL", "Blanco"), 1);
        givenAvailable(variant(second, "S", "Negro"), 1);
        Product third = stored(product(summer, "Tercero", 2, List.of("XL"), List.of("Gris", "Verde", "Arena"), 1000));
        givenAvailable(variant(third, "XL", "Arena"), 1);
        persistAndForget();

        FilterOptions options = subject.options(Optional.empty());

        assertThat(options.sizes()).containsExactly("S", "XL", "M");
        assertThat(options.colors()).containsExactly("Rosa", "Blanco", "Celeste", "Arena");
    }

    @Test
    void givenListedProducts_whenAskingForOptions_thenDetailsAndPricesComeFromTheListedOnesOfTheCategory() {
        CategoryId summer = category("Verano", true).id();
        CategoryId winter = category("Invierno", true).id();
        CategoryId closed = category("Cerrada", false).id();
        stocked(product(summer, "Modal", 0, List.of("S", "M"), List.of("Rosa"), 9000,
            p -> p.material("Modal").audience(Audience.KIDS).price("S", "Rosa", 2500)), 0);
        stocked(product(summer, "Algodón", 1, List.of("M"), List.of("Rosa"), 7000, p -> p.season(Season.MID_SEASON)),
            0);
        stocked(product(summer, "Oculto", 2, List.of("M"), List.of("Rosa"), 100, p -> p.material("Seda").hidden()),
            1);
        stocked(product(winter, "Polar", 3, List.of("M"), List.of("Rosa"), 12000,
            p -> p.material("Polar").season(Season.WINTER).audience(Audience.MEN)), 1);
        stocked(product(closed, "Cerrado", 4, List.of("M"), List.of("Rosa"), 50, p -> p.material("Lana")), 1);
        persistAndForget();

        assertThat(subject.options(Optional.of(summer))).isEqualTo(new FilterOptions(List.of(), List.of(),
            List.of("Algodón", "Modal"), List.of(Season.SUMMER, Season.MID_SEASON),
            List.of(Audience.WOMEN, Audience.KIDS), Optional.of(Money.of(2500)), Optional.of(Money.of(7000))));
        assertThat(subject.options(Optional.empty())).isEqualTo(new FilterOptions(List.of("M"), List.of("Rosa"),
            List.of("Algodón", "Modal", "Polar"), List.of(Season.SUMMER, Season.MID_SEASON, Season.WINTER),
            List.of(Audience.WOMEN, Audience.MEN, Audience.KIDS), Optional.of(Money.of(2500)),
            Optional.of(Money.of(12000))));
    }
}
