package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.T0;
import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.SearchText;
import com.pijamaparty.store.catalog.domain.Size;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contract of {@link ProductSearchQuery} over the vectors that {@link SearchVectors} refreshes (UC-26). */
public abstract class ProductSearchContractTest<T extends ProductSearchQuery & SearchVectors>
    extends ListedProductsContractTest<T> {

    private static final List<String> M = List.of("M");
    private static final List<String> ROSA = List.of("Rosa");

    private Category summer;

    /** Stores a change to a category that was given before. */
    protected abstract void givenCategoryUpdated(Category category);

    @BeforeEach
    void createCategory() {
        summer = category("Verano", true);
    }

    @Test
    void givenAProductWithAnAccent_whenSearchingWithoutItOrInCapitals_thenItAppears() {
        indexed(product(summer.id(), "Pijama de algodón", 0, M, ROSA, 1000, p -> p.material("Modal")));
        indexed(product(summer.id(), "Camisón", 1, M, ROSA, 1000, p -> p.material("Modal")));
        persistAndForget();

        assertThat(names("algodon")).containsExactly("Pijama de algodón");
        assertThat(names("ALGODÓN")).containsExactly("Pijama de algodón");
    }

    @Test
    void givenASingularName_whenSearchingThePlural_thenItAppears() {
        indexed(product(summer.id(), "Pijama de algodón", 0, M, ROSA, 1000));
        persistAndForget();

        assertThat(names("pijamas")).containsExactly("Pijama de algodón");
    }

    @Test
    void givenMatchesByNameAndByDescription_whenSearching_thenTheNameMatchesComeFirst() {
        indexed(product(summer.id(), "Camisón de seda", 0, M, ROSA, 1000));
        indexed(product(summer.id(), "Pijama clásico", 1, M, ROSA, 1000, p -> p.description("Tela de seda suave.")));
        persistAndForget();

        assertThat(names("seda")).containsExactly("Camisón de seda", "Pijama clásico");
    }

    @Test
    void givenTheCategoryMaterialAndActiveColors_whenSearching_thenTheyMatchButInactiveColorsDoNot() {
        indexed(product(summer.id(), "Pijama", 0, M, List.of("Rosa", "Celeste"), 1000,
            p -> p.material("Modal").inactive("M", "Celeste")));
        persistAndForget();

        assertThat(names("verano")).containsExactly("Pijama");
        assertThat(names("modal")).containsExactly("Pijama");
        assertThat(names("rosa")).containsExactly("Pijama");
        assertThat(names("celeste")).isEmpty();
    }

    @Test
    void givenHiddenProductsAndInactiveCategories_whenSearching_thenOnlyListedProductsAppear() {
        Category closed = category("Invierno", false);
        indexed(product(summer.id(), "Pijama publicado", 0, M, ROSA, 1000));
        indexed(product(summer.id(), "Pijama oculto", 1, M, ROSA, 1000, p -> p.hidden()));
        indexed(product(closed.id(), "Pijama de invierno", 2, M, ROSA, 1000));
        persistAndForget();

        assertThat(names("pijama")).containsExactly("Pijama publicado");
    }

    @Test
    void givenASearchWithSizeM_whenFiltered_thenOnlyProductsWithAnMVariantInStockAppear() {
        indexed(product(summer.id(), "Pijama M", 0, List.of("S", "M"), ROSA, 1000));
        Product onlyS = stored(product(summer.id(), "Pijama S", 1, List.of("S", "M"), ROSA, 1000));
        givenAvailable(ListingFixture.variant(onlyS, "S", "Rosa"), 2);
        subject.refreshProduct(onlyS.id());
        persistAndForget();

        var sizeM = new ProductFilter(Optional.empty(), Set.of(new Size("M")), Set.of(), Set.of(), Set.of(), Set.of(),
            Optional.empty(), Optional.empty());
        assertThat(names("pijama", sizeM, Optional.empty())).containsExactly("Pijama M");
    }

    @Test
    void givenQuotesSymbolsAndInjectionAttempts_whenSearching_thenTheyAreTreatedAsPlainText() {
        indexed(product(summer.id(), "Pijama", 0, M, ROSA, 1000));
        persistAndForget();

        assertThat(names("'; drop table product; --")).isEmpty();
        assertThat(names("\"pijama")).containsExactly("Pijama");
        assertThat(names("pijama:* & | ! ( \\")).containsExactly("Pijama");
        assertThat(suggestions("'; drop table product; --")).isEmpty();
        assertThat(suggestions("pij:* & !(")).containsExactly("Pijama");
    }

    @Test
    void givenAProductNotRefreshed_whenSearching_thenItDoesNotMatchUntilItIs() {
        Product product = stocked(product(summer.id(), "Pijama", 0, M, ROSA, 1000), 1);
        persistAndForget();

        assertThat(names("pijama")).isEmpty();

        subject.refreshProduct(product.id());
        persistAndForget();

        assertThat(names("pijama")).containsExactly("Pijama");
    }

    @Test
    void givenARenamedCategory_whenItsProductsAreRefreshed_thenTheNewNameMatches() {
        indexed(product(summer.id(), "Pijama", 0, M, ROSA, 1000));
        summer.rename(new CategoryName("Noche"), T0);
        givenCategoryUpdated(summer);

        subject.refreshCategory(summer.id());
        persistAndForget();

        assertThat(names("noche")).containsExactly("Pijama");
        assertThat(names("verano")).isEmpty();
    }

    @Test
    void givenAnExplicitSort_whenSearching_thenItReplacesRelevance() {
        indexed(product(summer.id(), "Pijama caro", 0, M, ROSA, 9000));
        indexed(product(summer.id(), "Camisón", 1, M, ROSA, 1000, p -> p.description("Como un pijama.")));
        persistAndForget();

        assertThat(names("pijama")).containsExactly("Pijama caro", "Camisón");
        assertThat(names("pijama", ProductFilter.none(), Optional.of(ProductSort.PRICE_ASC)))
            .containsExactly("Camisón", "Pijama caro");
        assertThat(names("pijama", ProductFilter.none(), Optional.of(ProductSort.NEWEST)))
            .containsExactly("Camisón", "Pijama caro");
    }

    @Test
    void givenMoreMatchesThanTheLimit_whenSearching_thenOffsetAndLimitPageThem() {
        for (int i = 1; i <= 5; i++) {
            indexed(product(summer.id(), "Pijama " + i, i, M, ROSA, 1000));
        }
        persistAndForget();

        assertThat(subject.search(text("pijama"), ProductFilter.none(), Optional.empty(), 0, 2))
            .extracting(ListedProduct::name).containsExactly("Pijama 5", "Pijama 4");
        assertThat(subject.search(text("pijama"), ProductFilter.none(), Optional.empty(), 4, 2))
            .extracting(ListedProduct::name).containsExactly("Pijama 1");
    }

    @Test
    void givenMatchesAndAFilter_whenCounting_thenEveryMatchCountsWhateverThePage() {
        for (int i = 1; i <= 3; i++) {
            indexed(product(summer.id(), "Pijama " + i, i, M, ROSA, 1000L * i));
        }
        indexed(product(summer.id(), "Camisón", 4, M, ROSA, 1000));
        persistAndForget();

        assertThat(subject.count(text("pijama"), ProductFilter.none())).isEqualTo(3);
        assertThat(subject.count(text("pijama"), new ProductFilter(Optional.empty(), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(), Optional.of(Money.of(2000)), Optional.empty()))).isEqualTo(2);
        assertThat(subject.count(text("seda"), ProductFilter.none())).isZero();
    }

    @Test
    void givenTheStartOfTheLastWord_whenSuggesting_thenListedProductsWithAWordStartingSoAppear() {
        indexed(product(summer.id(), "Pijama de algodón", 0, M, ROSA, 1000, p -> p.material("Modal").images(1)));
        indexed(product(summer.id(), "Pijama oculto", 1, M, ROSA, 1000, p -> p.hidden()));
        indexed(product(summer.id(), "Camisón", 2, M, ROSA, 1000, p -> p.material("Modal")));
        persistAndForget();

        assertThat(suggestions("pija")).containsExactly("Pijama de algodón");
        assertThat(suggestions("pijama alg")).containsExactly("Pijama de algodón");
        assertThat(suggestions("algodon pij")).containsExactly("Pijama de algodón");
        assertThat(suggestions("camisa pij")).isEmpty();
        ProductSuggestion suggestion = subject.suggest(text("pija"), 5).getFirst();
        assertThat(suggestion.slug()).isEqualTo("pijama-de-algodon");
        assertThat(suggestion.image()).isPresent();
    }

    @Test
    void givenManyMatches_whenSuggesting_thenTheLimitHoldsAndNameMatchesComeFirst() {
        indexed(product(summer.id(), "Camisón de seda", 0, M, ROSA, 1000));
        for (int i = 1; i <= 5; i++) {
            indexed(product(summer.id(), "Pijama " + i, i, M, ROSA, 1000, p -> p.description("Tela de seda.")));
        }
        persistAndForget();

        List<String> names = subject.suggest(text("sed"), 3).stream().map(ProductSuggestion::name).toList();

        assertThat(names).containsExactly("Camisón de seda", "Pijama 5", "Pijama 4");
    }

    /** Stores the product with stock in every variant and refreshes its vector. */
    private void indexed(Product product) {
        stocked(product, 1);
        subject.refreshProduct(product.id());
    }

    private List<String> names(String text) {
        return names(text, ProductFilter.none(), Optional.empty());
    }

    private List<String> names(String text, ProductFilter filter, Optional<ProductSort> sort) {
        return subject.search(text(text), filter, sort, 0, 100).stream().map(ListedProduct::name).toList();
    }

    private List<String> suggestions(String text) {
        return subject.suggest(text(text), 5).stream().map(ProductSuggestion::name).toList();
    }

    private static SearchText text(String value) {
        return SearchText.parse(value).orElseThrow();
    }
}
