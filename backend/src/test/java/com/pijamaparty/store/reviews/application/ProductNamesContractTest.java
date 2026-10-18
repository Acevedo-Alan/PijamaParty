package com.pijamaparty.store.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link ProductNames} (UC-28). */
public abstract class ProductNamesContractTest<T extends ProductNames> extends ContractTest<T> {

    /** A product with this name, published or not. */
    protected abstract void givenProduct(ProductId product, String name, boolean published);

    @Test
    void givenAPublishedAnUnpublishedAndAnUnknownProduct_whenAskingTheirNames_thenTheUnknownOneIsLeftOut() {
        ProductId luna = ProductId.of(UUID.randomUUID());
        ProductId sol = ProductId.of(UUID.randomUUID());
        givenProduct(luna, "Pijama Luna", true);
        givenProduct(sol, "Camisón Sol", false);

        assertThat(subject.namesOf(List.of(luna, sol, ProductId.of(UUID.randomUUID()), luna)))
            .isEqualTo(Map.of(luna, "Pijama Luna", sol, "Camisón Sol"));
    }

    @Test
    void givenNoProducts_whenAskingTheirNames_thenNoneComeBack() {
        assertThat(subject.namesOf(List.of())).isEmpty();
    }
}
