package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.category;
import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static com.pijamaparty.store.catalog.application.ListingFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.Season;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** UC-01, UC-02: the public grid and the options of its filter panel. */
class CatalogListingServicesTest {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryProductStore products = new InMemoryProductStore(categories);
    private final InMemoryProductListing listing = new InMemoryProductListing(categories, products);
    private final ListProducts list = new ListProductsService(categories, listing);
    private final GetFilterOptions options = new GetFilterOptionsService(categories, listing);

    private final Category summer = saved(category("Verano", true));

    @Test
    void givenMoreThan24Matches_whenTheCatalogOpens_thenPagesOf24ComeWithWhetherMoreFollow() {
        for (int i = 0; i <= ListProducts.PAGE_SIZE; i++) {
            stocked(product(summer.id(), "Pijama " + i, i, List.of("M"), List.of("Rosa"), 1000));
        }

        var first = (ListProducts.Result.Listed) list.execute(query(Optional.empty(), List.of(), Set.of(), 0));
        var second = (ListProducts.Result.Listed) list.execute(query(Optional.empty(), List.of(), Set.of(), 1));

        assertThat(first.products()).hasSize(ListProducts.PAGE_SIZE);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.products()).extracting(ProductListingItem::name).containsExactly("Pijama 0");
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void givenSizeMAndSeasonSummer_whenApplied_thenOnlyProductsWithAnMVariantInStockAndSeasonSummerAppear() {
        stocked(product(summer.id(), "Verano M", 0, List.of("M"), List.of("Rosa"), 1000));
        stocked(product(summer.id(), "Invierno M", 1, List.of("M"), List.of("Rosa"), 1000,
            p -> p.season(Season.WINTER)));
        Product soldOutM = saved(product(summer.id(), "Verano M agotado", 2, List.of("S", "M"), List.of("Rosa"),
            1000));
        listing.setAvailable(variant(soldOutM, "S", "Rosa"), 1);

        var listed = (ListProducts.Result.Listed) list.execute(
            query(Optional.empty(), List.of("M"), Set.of(Season.SUMMER), 0));

        assertThat(listed.products()).extracting(ProductListingItem::name).containsExactly("Verano M");
    }

    @Test
    void givenACategory_whenListed_thenOnlyItsProductsAppearAndAnUnknownOrInactiveOneIsNotFound() {
        Category winter = saved(category("Invierno", true));
        saved(category("Cerrada", false));
        stocked(product(summer.id(), "De verano", 0, List.of("M"), List.of("Rosa"), 1000));
        stocked(product(winter.id(), "De invierno", 1, List.of("M"), List.of("Rosa"), 1000));

        var listed = (ListProducts.Result.Listed) list.execute(query(Optional.of("invierno"), List.of(), Set.of(), 0));

        assertThat(listed.products()).extracting(ProductListingItem::name).containsExactly("De invierno");
        assertThat(list.execute(query(Optional.of("cerrada"), List.of(), Set.of(), 0)))
            .isEqualTo(new ListProducts.Result.UnknownCategory());
        assertThat(list.execute(query(Optional.of("no-existe"), List.of(), Set.of(), 0)))
            .isEqualTo(new ListProducts.Result.UnknownCategory());
    }

    @Test
    void givenAnInvalidFilter_whenListed_thenItIsRejected() {
        var minAboveMax = new ListProducts.Query(Optional.empty(), List.of(), List.of(), List.of(), Set.of(), Set.of(),
            Optional.of(2000L), Optional.of(1000L), ProductSort.NEWEST, 0);

        assertThat(list.execute(minAboveMax)).isEqualTo(new ListProducts.Result.InvalidFilter());
        assertThat(list.execute(query(Optional.empty(), List.of("x".repeat(100)), Set.of(), 0)))
            .isEqualTo(new ListProducts.Result.InvalidFilter());
    }

    @Test
    void givenACategory_whenAskingForFilterOptions_thenTheyComeFromItsProductsAndAnUnknownOneIsNotFound() {
        stocked(product(summer.id(), "Pijama", 0, List.of("S", "M"), List.of("Rosa"), 1000));

        assertThat(options.execute(new GetFilterOptions.Query(Optional.of("verano"))))
            .isEqualTo(new GetFilterOptions.Result.Found(listing.options(Optional.of(summer.id()))));
        assertThat(options.execute(new GetFilterOptions.Query(Optional.empty())))
            .isEqualTo(new GetFilterOptions.Result.Found(listing.options(Optional.empty())));
        assertThat(options.execute(new GetFilterOptions.Query(Optional.of("no-existe"))))
            .isEqualTo(new GetFilterOptions.Result.UnknownCategory());
    }

    private static ListProducts.Query query(Optional<String> category, List<String> sizes, Set<Season> seasons,
                                            int page) {
        return new ListProducts.Query(category, sizes, List.of(), List.of(), seasons, Set.of(), Optional.empty(),
            Optional.empty(), ProductSort.NEWEST, page);
    }

    private Category saved(Category category) {
        categories.save(category);
        return category;
    }

    private Product saved(Product product) {
        products.save(product);
        return product;
    }

    private void stocked(Product product) {
        saved(product).variants().forEach(variant -> listing.setAvailable(variant.id(), 1));
    }
}
