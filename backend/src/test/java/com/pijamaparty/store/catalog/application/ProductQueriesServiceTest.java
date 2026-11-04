package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductState;
import com.pijamaparty.store.catalog.domain.ProductStock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** UC-06: the back-office list and detail of products. */
class ProductQueriesServiceTest {

    private final ProductFixture fixture = new ProductFixture();

    @Test
    void givenMoreThanAPageOfProducts_whenListing_thenPagesOf50ComeWithWhetherMoreFollow() {
        IntStream.rangeClosed(1, ListAllProducts.PAGE_SIZE + 1).forEach(i -> fixture.created("Pijama " + i));

        var first = (ListAllProducts.Result.Listed) fixture.listAll.execute(new ListAllProducts.Query(ADMIN, 0));
        var second = (ListAllProducts.Result.Listed) fixture.listAll.execute(new ListAllProducts.Query(ADMIN, 1));

        assertThat(first.products()).hasSize(ListAllProducts.PAGE_SIZE);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.products()).hasSize(1);
        assertThat(second.hasMore()).isFalse();
        assertThat(fixture.listAll.execute(new ListAllProducts.Query(CUSTOMER, 0)))
            .isEqualTo(new ListAllProducts.Result.Forbidden());
        assertThatThrownBy(() -> new ListAllProducts.Query(ADMIN, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenSearches_whenListing_thenOneOf1CharacterOrOver100IsInvalidAndABlankOneIsNoSearch() {
        fixture.created("Pijama Corazón");
        fixture.created("Bata");

        assertThat(fixture.listAll.execute(searching("a"))).isEqualTo(new ListAllProducts.Result.InvalidSearch());
        assertThat(fixture.listAll.execute(searching("a".repeat(101))))
            .isEqualTo(new ListAllProducts.Result.InvalidSearch());
        assertThat(names(fixture.listAll.execute(searching("   ")))).containsExactlyInAnyOrder("Pijama Corazón", "Bata");
        assertThat(names(fixture.listAll.execute(searching("  corazon ")))).containsExactly("Pijama Corazón");
        assertThat(names(fixture.listAll.execute(searching("c".repeat(100))))).isEmpty();
        assertThat(fixture.listAll.execute(new ListAllProducts.Query(CUSTOMER, Optional.of("a"), Optional.empty(),
            Optional.empty(), Optional.empty(), 0))).isEqualTo(new ListAllProducts.Result.Forbidden());
    }

    @Test
    void givenAVariantWith2Available_whenFilteringByStock_thenTheThresholdOfTheStoreSettingsDecidesItsLevel() {
        ProductView product = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa"));
        fixture.products.setAvailable(product.variants().getFirst().id(), 2);

        assertThat(names(fixture.listAll.execute(filteringBy(ProductStock.LOW)))).containsExactly("Pijama");
        fixture.lowStockThreshold.set(1);
        assertThat(names(fixture.listAll.execute(filteringBy(ProductStock.LOW)))).isEmpty();
        assertThat(names(fixture.listAll.execute(filteringBy(ProductStock.IN_STOCK)))).containsExactly("Pijama");
    }

    @Test
    void givenACategoryAndAState_whenListing_thenTheyReachTheFilter() {
        fixture.created("Oculto");

        assertThat(names(fixture.listAll.execute(new ListAllProducts.Query(ADMIN, Optional.empty(),
            Optional.of(ProductFixture.CATEGORY), Optional.of(ProductState.HIDDEN), Optional.empty(), 0))))
            .containsExactly("Oculto");
        assertThat(names(fixture.listAll.execute(new ListAllProducts.Query(ADMIN, Optional.empty(),
            Optional.of(ProductFixture.CATEGORY), Optional.of(ProductState.PUBLISHED), Optional.empty(), 0))))
            .isEmpty();
    }

    private static ListAllProducts.Query searching(String text) {
        return new ListAllProducts.Query(ADMIN, Optional.of(text), Optional.empty(), Optional.empty(),
            Optional.empty(), 0);
    }

    private static ListAllProducts.Query filteringBy(ProductStock stock) {
        return new ListAllProducts.Query(ADMIN, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(stock), 0);
    }

    private static List<String> names(ListAllProducts.Result result) {
        return ((ListAllProducts.Result.Listed) result).products().stream().map(ProductSummaryView::name).toList();
    }

    @Test
    void givenAProduct_whenOpenedInTheBackOffice_thenItComesWithItsVariants() {
        ProductView product = fixture.withMatrix("Pijama", List.of("S", "M"), List.of("Rosa"));

        assertThat(fixture.get.execute(new GetProductForAdmin.Query(ADMIN, product.id())))
            .isEqualTo(new GetProductForAdmin.Result.Found(product));
        assertThat(fixture.get.execute(new GetProductForAdmin.Query(ADMIN, ProductId.of(UUID.randomUUID()))))
            .isEqualTo(new GetProductForAdmin.Result.NotFound());
        assertThat(fixture.get.execute(new GetProductForAdmin.Query(CUSTOMER, product.id())))
            .isEqualTo(new GetProductForAdmin.Result.Forbidden());
    }
}
