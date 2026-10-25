package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.PLACEHOLDER;
import static com.pijamaparty.store.catalog.application.ListingFixture.T0;
import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static com.pijamaparty.store.catalog.application.ListingFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.Material;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Size;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contract of {@link ProductListingQuery} (UC-01, UC-02). */
public abstract class ProductListingQueryContractTest<T extends ProductListingQuery>
    extends ListedProductsContractTest<T> {

    private static final List<String> M = List.of("M");
    private static final List<String> ROSA = List.of("Rosa");

    private CategoryId summer;

    @BeforeEach
    void createCategory() {
        summer = category("Verano", true).id();
    }

    @Test
    void givenHiddenProductsAndInactiveCategories_whenListing_thenOnlyPublishedProductsOfActiveCategoriesAppear() {
        CategoryId inactive = category("Invierno", false).id();
        stocked(product(summer, "Publicado", 0, M, ROSA, 1000), 1);
        stocked(product(summer, "Oculto", 1, M, ROSA, 1000, p -> p.hidden()), 1);
        stocked(product(inactive, "De categoría inactiva", 2, M, ROSA, 1000), 1);
        persistAndForget();

        assertThat(names(ProductFilter.none())).containsExactly("Publicado");
    }

    @Test
    void givenVariants_whenListing_thenTheFromPriceIsTheLowestOfTheActivePricedOnes() {
        Product product = stocked(product(summer, "Pijama", 0, List.of("S", "M", "L"), ROSA, 5000, p -> p
            .price("M", "Rosa", 4000)
            .price("L", "Rosa", 1000)
            .inactive("L", "Rosa")
            .unpriced("S", "Rosa")), 0);
        givenAvailable(variant(product, "M", "Rosa"), 2);
        persistAndForget();

        ListedProduct item = list(ProductFilter.none(), ProductSort.NEWEST).getFirst();

        assertThat(item.slug()).isEqualTo("pijama");
        assertThat(item.name()).isEqualTo("Pijama");
        assertThat(item.fromPrice()).isEqualTo(Money.of(4000));
        assertThat(item.soldOut()).isFalse();
        assertThat(item.createdAt()).isEqualTo(T0);
    }

    @Test
    void givenStockInSomeColors_whenListing_thenTheColorsAreThoseOfActivePricedVariantsWithStockInDisplayOrder() {
        Product product = stored(product(summer, "Pijama", 0, List.of("S", "M"),
            List.of("Celeste", "Beige", "Rosa", "Lila"), 1000, p -> p.inactive("M", "Lila").unpriced("S", "Lila")));
        givenAvailable(variant(product, "S", "Rosa"), 3);
        givenAvailable(variant(product, "M", "Rosa"), 1);
        givenAvailable(variant(product, "S", "Celeste"), 2);
        givenAvailable(variant(product, "M", "Lila"), 5);
        givenAvailable(variant(product, "S", "Lila"), 5);
        stored(product(summer, "Agotado", 1, M, ROSA, 1000));
        persistAndForget();

        assertThat(list(ProductFilter.none(), ProductSort.NEWEST))
            .extracting(ListedProduct::name, ListedProduct::colors)
            .containsExactly(
                tuple("Agotado", List.of()),
                tuple("Pijama", List.of("Celeste", "Rosa")));
    }

    @Test
    void givenFilters_whenCounting_thenEveryListedMatchCountsWhateverThePage() {
        for (int i = 1; i <= 5; i++) {
            stocked(product(summer, "Pijama " + i, i, M, ROSA, 1000L * i), 1);
        }
        stocked(product(summer, "Oculto", 6, M, ROSA, 1000, p -> p.hidden()), 1);
        persistAndForget();

        assertThat(subject.count(ProductFilter.none())).isEqualTo(5);
        assertThat(subject.count(filter(f -> f.minPrice(3000)))).isEqualTo(3);
        assertThat(subject.count(filter(f -> f.sizes(Set.of(new Size("S")))))).isZero();
    }

    @Test
    void givenNoActivePricedVariantWithStock_whenListing_thenTheProductIsSoldOut() {
        Product inactiveInStock = stored(product(summer, "Inactiva con stock", 0, List.of("S", "M"), ROSA, 1000,
            p -> p.inactive("S", "Rosa")));
        givenAvailable(variant(inactiveInStock, "S", "Rosa"), 5);
        givenAvailable(variant(inactiveInStock, "M", "Rosa"), 0);
        stored(product(summer, "Sin stock cargado", 1, M, ROSA, 1000));
        persistAndForget();

        assertThat(list(ProductFilter.none(), ProductSort.NEWEST))
            .extracting(ListedProduct::name, ListedProduct::soldOut)
            .containsExactly(
                tuple("Sin stock cargado", true),
                tuple("Inactiva con stock", true));
    }

    @Test
    void givenSizeAndColor_whenFiltering_thenOneActiveVariantWithStockMustHaveBoth() {
        Product exact = stored(product(summer, "Rosa M", 0, List.of("S", "M"), List.of("Rosa", "Celeste"), 1000));
        givenAvailable(variant(exact, "M", "Rosa"), 1);
        Product crossed = stored(product(summer, "Cruzado", 1, List.of("S", "M"), List.of("Rosa", "Celeste"), 1000));
        givenAvailable(variant(crossed, "M", "Celeste"), 1);
        givenAvailable(variant(crossed, "S", "Rosa"), 1);
        Product inactive = stored(product(summer, "Inactiva", 2, List.of("S", "M"), ROSA, 1000,
            p -> p.inactive("M", "Rosa")));
        givenAvailable(variant(inactive, "M", "Rosa"), 5);
        persistAndForget();

        assertThat(names(filter(f -> f.sizes(Set.of(new Size("M"))).colors(Set.of(new Color("Rosa"))))))
            .containsExactly("Rosa M");
        assertThat(names(filter(f -> f.sizes(Set.of(new Size("M"))))))
            .containsExactly("Cruzado", "Rosa M");
        assertThat(names(filter(f -> f.colors(Set.of(new Color("Celeste"), new Color("Rosa"))))))
            .containsExactly("Cruzado", "Rosa M");
    }

    @Test
    void givenProductDetails_whenFiltering_thenFiltersCombineWithAndAndTheirValuesWithOr() {
        CategoryId kids = category("Infantil", true).id();
        stocked(product(summer, "Verano mujer", 0, M, ROSA, 1000), 1);
        stocked(product(summer, "Invierno mujer", 1, M, ROSA, 1000, p -> p.season(Season.WINTER)), 1);
        stocked(product(summer, "Verano hombre modal", 2, M, ROSA, 1000,
            p -> p.audience(Audience.MEN).material("Modal")), 1);
        stocked(product(kids, "Verano infantil", 3, M, ROSA, 1000, p -> p.audience(Audience.KIDS)), 1);
        persistAndForget();

        assertThat(names(filter(f -> f.seasons(Set.of(Season.SUMMER)).audiences(Set.of(Audience.WOMEN)))))
            .containsExactly("Verano mujer");
        assertThat(names(filter(f -> f.audiences(Set.of(Audience.MEN, Audience.KIDS)))))
            .containsExactly("Verano infantil", "Verano hombre modal");
        assertThat(names(filter(f -> f.materials(Set.of(new Material("Modal")))))).containsExactly("Verano hombre modal");
        assertThat(names(filter(f -> f.category(kids)))).containsExactly("Verano infantil");
        assertThat(names(filter(f -> f.category(kids).seasons(Set.of(Season.WINTER))))).isEmpty();
    }

    @Test
    void givenAPriceRange_whenFiltering_thenItAppliesToTheFromPriceWithBoundsIncluded() {
        stocked(product(summer, "Barato", 0, List.of("S", "M"), ROSA, 9000, p -> p.price("S", "Rosa", 1000)), 1);
        stocked(product(summer, "Medio", 1, M, ROSA, 5000), 1);
        stocked(product(summer, "Caro", 2, M, ROSA, 9000), 1);
        persistAndForget();

        assertThat(names(filter(f -> f.minPrice(5000).maxPrice(9000)))).containsExactly("Caro", "Medio");
        assertThat(names(filter(f -> f.maxPrice(4999)))).containsExactly("Barato");
        assertThat(names(filter(f -> f.minPrice(9001)))).isEmpty();
    }

    @Test
    void givenProducts_whenSorting_thenPricesUseTheFromPriceAndTiesGoNewestFirst() {
        stocked(product(summer, "Viejo 2000", 0, M, ROSA, 2000), 1);
        stocked(product(summer, "Nuevo 2000", 1, M, ROSA, 2000), 1);
        stocked(product(summer, "Más nuevo 1000", 2, M, ROSA, 1000), 1);
        stocked(product(summer, "Medio 3000", 1, List.of("S", "M"), ROSA, 3000), 1);
        persistAndForget();

        assertThat(names(ProductFilter.none(), ProductSort.NEWEST)).startsWith("Más nuevo 1000").endsWith("Viejo 2000");
        assertThat(names(ProductFilter.none(), ProductSort.PRICE_ASC))
            .containsExactly("Más nuevo 1000", "Nuevo 2000", "Viejo 2000", "Medio 3000");
        assertThat(names(ProductFilter.none(), ProductSort.PRICE_DESC))
            .containsExactly("Medio 3000", "Nuevo 2000", "Viejo 2000", "Más nuevo 1000");
    }

    @Test
    void givenMoreProductsThanTheLimit_whenListing_thenOffsetAndLimitPageThem() {
        for (int i = 1; i <= 5; i++) {
            stocked(product(summer, "Pijama " + i, i, M, ROSA, 1000), 1);
        }
        persistAndForget();

        assertThat(subject.list(ProductFilter.none(), ProductSort.NEWEST, 0, 2))
            .extracting(ListedProduct::name).containsExactly("Pijama 5", "Pijama 4");
        assertThat(subject.list(ProductFilter.none(), ProductSort.NEWEST, 4, 2))
            .extracting(ListedProduct::name).containsExactly("Pijama 1");
    }

    @Test
    void givenPhotos_whenListing_thenTheFirstOneIsShownAndAProductWithoutPhotosHasNone() {
        Product withPhotos = stocked(product(summer, "Con fotos", 0, M, ROSA, 1000, p -> p.images(2)), 1);
        stocked(product(summer, "Sin fotos", 1, M, ROSA, 1000), 1);
        persistAndForget();

        List<ListedProduct> items = list(ProductFilter.none(), ProductSort.NEWEST);

        assertThat(items.get(0).image()).isEmpty();
        var first = withPhotos.images().getFirst();
        assertThat(items.get(1).image()).contains(ProductListingItem.ListingImage.of(first.storageKey(),
            first.placeholder()));
        assertThat(items.get(1).image().orElseThrow().placeholder()).isEqualTo(PLACEHOLDER);
    }

    @Test
    void givenTwoPhotosOneOrNone_whenListing_thenTheSecondPhotoIsTheOneAfterTheFirstOrNone() {
        Product two = stocked(product(summer, "Dos fotos", 0, M, ROSA, 1000, p -> p.images(2)), 1);
        stocked(product(summer, "Una foto", 1, M, ROSA, 1000, p -> p.images(1)), 1);
        stocked(product(summer, "Sin fotos", 2, M, ROSA, 1000), 1);
        persistAndForget();

        List<ListedProduct> items = list(ProductFilter.none(), ProductSort.NEWEST);

        assertThat(items).extracting(ListedProduct::name).containsExactly("Sin fotos", "Una foto", "Dos fotos");
        assertThat(items.get(0).secondImage()).isEmpty();
        assertThat(items.get(1).image()).isPresent();
        assertThat(items.get(1).secondImage()).isEmpty();
        var second = two.images().get(1);
        assertThat(items.get(2).secondImage()).contains(ProductListingItem.ListingImage.of(second.storageKey(),
            second.placeholder()));
    }

    private List<ListedProduct> list(ProductFilter filter, ProductSort sort) {
        return subject.list(filter, sort, 0, 100);
    }

    private List<String> names(ProductFilter filter) {
        return names(filter, ProductSort.NEWEST);
    }

    private List<String> names(ProductFilter filter, ProductSort sort) {
        return list(filter, sort).stream().map(ListedProduct::name).toList();
    }

    private static ProductFilter filter(UnaryOperator<FilterBuilder> changes) {
        return changes.apply(new FilterBuilder()).build();
    }

    /** Builds a {@link ProductFilter} one filter at a time. */
    static final class FilterBuilder {

        private Optional<CategoryId> category = Optional.empty();
        private Set<Size> sizes = Set.of();
        private Set<Color> colors = Set.of();
        private Set<Material> materials = Set.of();
        private Set<Season> seasons = Set.of();
        private Set<Audience> audiences = Set.of();
        private Optional<Money> minPrice = Optional.empty();
        private Optional<Money> maxPrice = Optional.empty();

        FilterBuilder category(CategoryId value) {
            category = Optional.of(value);
            return this;
        }

        FilterBuilder sizes(Set<Size> values) {
            sizes = values;
            return this;
        }

        FilterBuilder colors(Set<Color> values) {
            colors = values;
            return this;
        }

        FilterBuilder materials(Set<Material> values) {
            materials = values;
            return this;
        }

        FilterBuilder seasons(Set<Season> values) {
            seasons = values;
            return this;
        }

        FilterBuilder audiences(Set<Audience> values) {
            audiences = values;
            return this;
        }

        FilterBuilder minPrice(long cents) {
            minPrice = Optional.of(Money.of(cents));
            return this;
        }

        FilterBuilder maxPrice(long cents) {
            maxPrice = Optional.of(Money.of(cents));
            return this;
        }

        ProductFilter build() {
            return new ProductFilter(category, sizes, colors, materials, seasons, audiences, minPrice, maxPrice);
        }
    }
}
