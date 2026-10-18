package com.pijamaparty.store.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link ProductVariants} (UC-24). */
public abstract class ProductVariantsContractTest<T extends ProductVariants> extends ContractTest<T> {

    /** A product, published or not, with one variant the catalog sells and one it no longer sells. */
    protected abstract void givenProduct(ProductId product, boolean published, UUID soldVariant, UUID unsoldVariant);

    @Test
    void givenAProduct_whenAskingItsVariants_thenEveryOneComesBackSoldOrNot() {
        ProductId product = ProductId.of(UUID.randomUUID());
        UUID sold = UUID.randomUUID();
        UUID unsold = UUID.randomUUID();
        givenProduct(product, true, sold, unsold);
        givenProduct(ProductId.of(UUID.randomUUID()), true, UUID.randomUUID(), UUID.randomUUID());

        assertThat(subject.ofProduct(product)).contains(Set.of(sold, unsold));
    }

    @Test
    void givenAnUnpublishedProduct_whenAskingItsVariants_thenTheyStillComeBack() {
        ProductId product = ProductId.of(UUID.randomUUID());
        UUID sold = UUID.randomUUID();
        UUID unsold = UUID.randomUUID();
        givenProduct(product, false, sold, unsold);

        assertThat(subject.ofProduct(product)).contains(Set.of(sold, unsold));
    }

    @Test
    void givenAnUnknownProduct_whenAskingItsVariants_thenThereIsNoSuchProduct() {
        assertThat(subject.ofProduct(ProductId.of(UUID.randomUUID()))).isEmpty();
    }
}
