package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.ProductId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-06: publishing and hiding a product. */
class ProductPublicationServicesTest {

    private final ProductFixture fixture = new ProductFixture();

    @Test
    void givenAHiddenProduct_whenPublished_thenItIsInTheCatalog() {
        ProductView product = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa"));
        fixture.priced(product.variants().getFirst(), 1_500_000);

        PublishProduct.Result result = fixture.publish.execute(new PublishProduct.Command(ADMIN, product.id()));

        assertThat(result).isInstanceOfSatisfying(PublishProduct.Result.Published.class,
            published -> assertThat(published.product().published()).isTrue());
        assertThat(fixture.products.findById(product.id()).orElseThrow().published()).isTrue();
        assertThat(fixture.listAll.execute(new ListAllProducts.Query(ADMIN, 0)))
            .isInstanceOfSatisfying(ListAllProducts.Result.Listed.class, listed ->
                assertThat(listed.products().getFirst().published()).isTrue());
    }

    @Test
    void givenAProductWithoutAnActivePricedVariant_whenPublished_thenItStaysHidden() {
        ProductView product = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa"));

        assertThat(fixture.publish.execute(new PublishProduct.Command(ADMIN, product.id())))
            .isEqualTo(new PublishProduct.Result.NoActivePricedVariant());
        assertThat(fixture.products.findById(product.id()).orElseThrow().published()).isFalse();
    }

    @Test
    void givenAPublishedProduct_whenHidden_thenItLeavesTheCatalog() {
        ProductView product = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa"));
        fixture.priced(product.variants().getFirst(), 1_500_000);
        fixture.publish.execute(new PublishProduct.Command(ADMIN, product.id()));

        HideProduct.Result result = fixture.hide.execute(new HideProduct.Command(ADMIN, product.id()));

        assertThat(result).isInstanceOfSatisfying(HideProduct.Result.Hidden.class,
            hidden -> assertThat(hidden.product().published()).isFalse());
        assertThat(fixture.products.findById(product.id()).orElseThrow().published()).isFalse();
    }

    @Test
    void givenAMissingProductOrAnotherActor_whenPublishingOrHiding_thenItIsRejected() {
        ProductId missing = ProductId.of(UUID.randomUUID());
        ProductView product = fixture.created("Pijama");

        assertThat(fixture.publish.execute(new PublishProduct.Command(ADMIN, missing)))
            .isEqualTo(new PublishProduct.Result.NotFound());
        assertThat(fixture.hide.execute(new HideProduct.Command(ADMIN, missing)))
            .isEqualTo(new HideProduct.Result.NotFound());
        assertThat(fixture.publish.execute(new PublishProduct.Command(CUSTOMER, product.id())))
            .isEqualTo(new PublishProduct.Result.Forbidden());
        assertThat(fixture.hide.execute(new HideProduct.Command(CUSTOMER, product.id())))
            .isEqualTo(new HideProduct.Result.Forbidden());
    }
}
