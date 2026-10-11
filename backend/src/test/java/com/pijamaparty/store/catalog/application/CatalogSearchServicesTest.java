package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.category;
import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductSort;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-26: the search, its suggestions and the refresh of the index. */
class CatalogSearchServicesTest {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryProductStore products = new InMemoryProductStore(categories);
    private final InMemoryProductListing listing = new InMemoryProductListing(categories, products);
    private final InMemoryProductSearch index = new InMemoryProductSearch(categories, products, listing);
    private final SearchProducts search = new SearchProductsService(categories, index);
    private final SuggestProducts suggest = new SuggestProductsService(index);
    private final RefreshSearchVector refresh = new RefreshSearchVectorService(index);

    private final Category summer = saved(category("Verano", true));

    @Test
    void givenMoreThan24Matches_whenSearching_thenPagesOf24ComeWithWhetherMoreFollow() {
        for (int i = 0; i <= SearchProducts.PAGE_SIZE; i++) {
            indexed(product(summer.id(), "Pijama " + i, i, List.of("M"), List.of("Rosa"), 1000));
        }

        var first = (SearchProducts.Result.Listed) search.execute(query("pijama", Optional.empty(), List.of(), 0));
        var second = (SearchProducts.Result.Listed) search.execute(query("pijama", Optional.empty(), List.of(), 1));

        assertThat(first.products()).hasSize(SearchProducts.PAGE_SIZE);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.products()).extracting(ProductListingItem::name).containsExactly("Pijama 0");
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void givenATextShorterThan2Characters_whenSearching_thenItIsNotASearchAndSuggestsNothing() {
        indexed(product(summer.id(), "Pijama", 0, List.of("M"), List.of("Rosa"), 1000));

        assertThat(search.execute(query(" p ", Optional.empty(), List.of(), 0)))
            .isEqualTo(new SearchProducts.Result.TextTooShort());
        assertThat(suggest.execute(new SuggestProducts.Query("p")))
            .isEqualTo(new SuggestProducts.Result.Suggested(List.of()));
        assertThat(suggest.execute(new SuggestProducts.Query("¿?!")))
            .isEqualTo(new SuggestProducts.Result.Suggested(List.of()));
    }

    @Test
    void givenACategoryOrInvalidFilters_whenSearching_thenTheyApplyAsInTheListing() {
        Category winter = saved(category("Invierno", true));
        saved(category("Cerrada", false));
        indexed(product(summer.id(), "Pijama de verano", 0, List.of("M"), List.of("Rosa"), 1000));
        indexed(product(winter.id(), "Pijama de invierno", 1, List.of("M"), List.of("Rosa"), 1000));

        var listed = (SearchProducts.Result.Listed) search.execute(
            query("pijama", Optional.of("invierno"), List.of(), 0));

        assertThat(listed.products()).extracting(ProductListingItem::name).containsExactly("Pijama de invierno");
        assertThat(search.execute(query("pijama", Optional.of("cerrada"), List.of(), 0)))
            .isEqualTo(new SearchProducts.Result.UnknownCategory());
        assertThat(search.execute(query("pijama", Optional.empty(), List.of("x".repeat(21)), 0)))
            .isEqualTo(new SearchProducts.Result.InvalidFilter());
    }

    @Test
    void givenATextLongerThan100Characters_whenSearching_thenOnlyTheFirst100Count() {
        indexed(product(summer.id(), "Pijama", 0, List.of("M"), List.of("Rosa"), 1000));

        var listed = (SearchProducts.Result.Listed) search.execute(
            query("pijama" + " ".repeat(94) + "camisón", Optional.empty(), List.of(), 0));

        assertThat(listed.products()).extracting(ProductListingItem::name).containsExactly("Pijama");
    }

    @Test
    void givenProducts_whenSuggesting_thenAtMost5ListedProductsAppear() {
        for (int i = 0; i < 7; i++) {
            indexed(product(summer.id(), "Pijama " + i, i, List.of("M"), List.of("Rosa"), 1000));
        }

        var suggested = (SuggestProducts.Result.Suggested) suggest.execute(new SuggestProducts.Query("pij"));

        assertThat(suggested.products()).extracting(ProductSuggestion::name)
            .containsExactly("Pijama 6", "Pijama 5", "Pijama 4", "Pijama 3", "Pijama 2");
    }

    @Test
    void givenARenamedCategoryOrAChangedProduct_whenRefreshed_thenTheIndexFollows() {
        Product product = saved(product(summer.id(), "Pijama", 0, List.of("M"), List.of("Rosa"), 1000));
        refresh.execute(new RefreshSearchVector.Command.OfProduct(product.id()));
        summer.rename(new CategoryName("Noche"), ListingFixture.T0);
        categories.save(summer);

        refresh.execute(new RefreshSearchVector.Command.OfCategory(summer.id()));

        var listed = (SearchProducts.Result.Listed) search.execute(query("noche", Optional.empty(), List.of(), 0));
        assertThat(listed.products()).extracting(ProductListingItem::name).containsExactly("Pijama");
    }

    @Test
    void givenActiveAndInactiveCategories_whenListedForTheShop_thenOnlyActiveOnesComeByPosition() {
        Category winter = saved(Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("Invierno"),
            categories.findAll(), ListingFixture.T0));
        saved(category("Cerrada", false));

        var listed = (ListShopCategories.Result.Listed) new ListShopCategoriesService(categories).execute();

        assertThat(listed.categories()).containsExactly(
            new ListShopCategories.ShopCategory("verano", "Verano"),
            new ListShopCategories.ShopCategory(winter.slug().value(), "Invierno"));
    }

    private static SearchProducts.Query query(String text, Optional<String> category, List<String> sizes, int page) {
        return new SearchProducts.Query(text, category, sizes, List.of(), List.of(), Set.of(), Set.of(),
            Optional.empty(), Optional.empty(), Optional.<ProductSort>empty(), page);
    }

    private Category saved(Category category) {
        categories.save(category);
        return category;
    }

    private Product saved(Product product) {
        products.save(product);
        product.variants().forEach(variant -> listing.setAvailable(variant.id(), 1));
        return product;
    }

    private void indexed(Product product) {
        saved(product);
        index.refreshProduct(product.id());
    }
}
