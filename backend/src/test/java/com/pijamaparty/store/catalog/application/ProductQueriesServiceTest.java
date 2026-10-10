package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.catalog.domain.ProductId;
import java.util.List;
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
