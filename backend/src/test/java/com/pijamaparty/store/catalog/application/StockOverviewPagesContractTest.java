package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static com.pijamaparty.store.catalog.application.ListingFixture.sku;
import static com.pijamaparty.store.catalog.application.ListingFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductSearch;
import com.pijamaparty.store.catalog.domain.StockFilter;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link StockOverviewPages} (UC-07, T-E8-09): the active variants of every product, published or not, by
 * available units (on hand minus reserved), then product name, size and color; a variant without stock has none.
 */
public abstract class StockOverviewPagesContractTest<T extends StockOverviewPages>
    extends ListedProductsContractTest<T> {

    private static final List<String> ROSA = List.of("Rosa");

    private CategoryId summer;

    /** The units of a variant held by orders awaiting payment (UC-16); its stock was given first. */
    protected abstract void givenReserved(VariantId variant, int reserved);

    @BeforeEach
    void createCategory() {
        summer = category("Verano", true).id();
    }

    @Test
    void givenAThresholdOf3AndVariantsWith0_2And5_whenFiltered_thenLowIsThe2AndOutIsThe0() {
        Product luna = stored(product(summer, "Pijama Luna", 0, List.of("S", "M", "L"), ROSA, 1000));
        givenAvailable(variant(luna, "S", "Rosa"), 0);
        givenAvailable(variant(luna, "M", "Rosa"), 2);
        givenAvailable(variant(luna, "L", "Rosa"), 5);
        persistAndForget();

        assertThat(sizes(StockOverviewFilter.of(StockFilter.LOW, 3))).containsExactly("M");
        assertThat(sizes(StockOverviewFilter.of(StockFilter.OUT, 3))).containsExactly("S");
        assertThat(sizes(StockOverviewFilter.of(StockFilter.ALL, 3))).containsExactly("S", "M", "L");
        assertThat(sizes(StockOverviewFilter.of(StockFilter.LOW, 1))).isEmpty();
    }

    @Test
    void givenUnitsOnHandAndReserved_whenListed_thenTheRowHasTheThreeAndTheProductsState() {
        Product luna = stored(product(summer, "Pijama Luna", 0, List.of("M"), ROSA, 1000, builder -> builder.hidden()));
        VariantId medium = variant(luna, "M", "Rosa");
        givenAvailable(medium, 4);
        givenReserved(medium, 3);
        persistAndForget();

        assertThat(subject.page(StockOverviewFilter.of(StockFilter.ALL, 3), 0, 10)).containsExactly(
            new StockOverviewRow(medium, luna.id(), "Pijama Luna", "M", "Rosa", sku(luna, medium), false, 4, 3, 1));
        assertThat(sizes(StockOverviewFilter.of(StockFilter.LOW, 3))).containsExactly("M");
    }

    @Test
    void givenSeveralProducts_whenListed_thenByAvailableThenNameThenSizeAndColorWithoutInactiveVariants() {
        Product sol = stored(product(summer, "Pijama Sol", 0, List.of("S", "M"), List.of("Rosa", "Azul"), 1000,
            builder -> builder.inactive("S", "Azul")));
        sol.variants().forEach(variant -> givenAvailable(variant.id(), 2));
        Product bata = stored(product(summer, "Bata", 1, List.of("M"), ROSA, 1000));
        givenAvailable(variant(bata, "M", "Rosa"), 2);
        Product noStock = stored(product(summer, "Camison", 2, List.of("M"), ROSA, 1000));
        persistAndForget();

        assertThat(subject.page(StockOverviewFilter.of(StockFilter.ALL, 3), 0, 10))
            .extracting(StockOverviewRow::productName, StockOverviewRow::size, StockOverviewRow::color,
                StockOverviewRow::available)
            .containsExactly(
                tuple("Camison", "M", "Rosa", 0),
                tuple("Bata", "M", "Rosa", 2),
                tuple("Pijama Sol", "S", "Rosa", 2),
                tuple("Pijama Sol", "M", "Rosa", 2),
                tuple("Pijama Sol", "M", "Azul", 2));
        assertThat(subject.page(StockOverviewFilter.of(StockFilter.ALL, 3), 2, 2))
            .extracting(StockOverviewRow::productName, StockOverviewRow::size)
            .containsExactly(tuple("Pijama Sol", "S"), tuple("Pijama Sol", "M"));
        assertThat(subject.page(StockOverviewFilter.of(StockFilter.OUT, 3), 0, 10))
            .extracting(StockOverviewRow::productId).containsExactly(noStock.id());
    }

    @Test
    void givenASearchAndACategory_whenListed_thenTheNameOrTheVariantsOwnSkuMatchesWithinTheCategory() {
        CategoryId winter = category("Invierno", true).id();
        Product heart = stored(product(summer, "Pijama Corazón", 0, List.of("S", "M"), ROSA, 1000));
        Product robe = stored(product(winter, "Bata", 1, List.of("M"), ROSA, 1000));
        VariantId small = variant(heart, "S", "Rosa");
        persistAndForget();

        assertThat(names(search("CORAZON", Optional.empty()))).containsExactly("Pijama Corazón", "Pijama Corazón");
        String smallSku = sku(heart, small);
        assertThat(subject.page(search(smallSku.toLowerCase(Locale.ROOT), Optional.empty()), 0, 10))
            .extracting(StockOverviewRow::variantId).containsExactly(small);
        assertThat(names(search(smallSku.substring(0, smallSku.length() - 1), Optional.empty()))).isEmpty();
        assertThat(names(new StockOverviewFilter(StockFilter.ALL, 3, Optional.empty(), Optional.of(winter))))
            .containsExactly("Bata");
        assertThat(names(search("pijama", Optional.of(winter)))).isEmpty();
        assertThat(robe.variants()).hasSize(1);
    }

    @Test
    void givenASearchWithLikeWildcards_whenListed_thenEveryCharacterIsLiteral() {
        stored(product(summer, "Descuento 50%", 0, List.of("M"), ROSA, 1000));
        stored(product(summer, "Pijama 500", 1, List.of("M"), ROSA, 1000));
        stored(product(summer, "Talle_S", 2, List.of("M"), ROSA, 1000));
        stored(product(summer, "TalleXS", 3, List.of("M"), ROSA, 1000));
        stored(product(summer, "Bata\\Luna", 4, List.of("M"), ROSA, 1000));
        persistAndForget();

        assertThat(names(search("0%", Optional.empty()))).containsExactly("Descuento 50%");
        assertThat(names(search("e_s", Optional.empty()))).containsExactly("Talle_S");
        assertThat(names(search("a\\l", Optional.empty()))).containsExactly("Bata\\Luna");
    }

    private static StockOverviewFilter search(String text, Optional<CategoryId> category) {
        return new StockOverviewFilter(StockFilter.ALL, 3, Optional.of(new ProductSearch(text)), category);
    }

    private List<String> names(StockOverviewFilter filter) {
        return subject.page(filter, 0, 100).stream().map(StockOverviewRow::productName).toList();
    }

    private List<String> sizes(StockOverviewFilter filter) {
        return subject.page(filter, 0, 100).stream().map(StockOverviewRow::size).toList();
    }
}
