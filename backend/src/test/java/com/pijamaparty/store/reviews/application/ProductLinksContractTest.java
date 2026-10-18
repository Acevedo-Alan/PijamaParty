package com.pijamaparty.store.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.reviews.application.ProductLinks.ProductLink;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link ProductLinks} (UC-25). */
public abstract class ProductLinksContractTest<T extends ProductLinks> extends ContractTest<T> {

    /** A product with this name at this slug, published or not. */
    protected abstract void givenProduct(ProductId product, String name, String slug, boolean published);

    @Test
    void givenAPublishedAnUnpublishedAndAnUnknownProduct_whenAskingTheirLinks_thenTheUnknownOneIsLeftOut() {
        ProductId luna = ProductId.of(UUID.randomUUID());
        ProductId sol = ProductId.of(UUID.randomUUID());
        givenProduct(luna, "Pijama Luna", "pijama-luna", true);
        givenProduct(sol, "Camisón Sol", "camison-sol", false);

        assertThat(subject.linksOf(List.of(luna, sol, ProductId.of(UUID.randomUUID()), luna))).isEqualTo(Map.of(
            luna, new ProductLink("Pijama Luna", "pijama-luna"),
            sol, new ProductLink("Camisón Sol", "camison-sol")));
    }

    @Test
    void givenNoProducts_whenAskingTheirLinks_thenNoneComeBack() {
        assertThat(subject.linksOf(List.of())).isEmpty();
    }
}
