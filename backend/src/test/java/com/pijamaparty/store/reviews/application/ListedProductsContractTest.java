package com.pijamaparty.store.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link ListedProducts} (UC-24, T-E5-07). */
public abstract class ListedProductsContractTest<T extends ListedProducts> extends ContractTest<T> {

    /** A product at {@code slug}, which visitors see, or not because it is hidden. */
    protected abstract void givenProduct(ProductId product, String slug, boolean listed);

    @Test
    void givenAListedProduct_whenLookingUpItsSlug_thenItsIdComesBack() {
        ProductId luna = ProductId.of(UUID.randomUUID());
        givenProduct(luna, "pijama-luna", true);
        givenProduct(ProductId.of(UUID.randomUUID()), "pijama-sol", true);

        assertThat(subject.bySlug("pijama-luna")).contains(luna);
    }

    @Test
    void givenAHiddenProduct_whenLookingUpItsSlug_thenVisitorsDoNotSeeIt() {
        givenProduct(ProductId.of(UUID.randomUUID()), "pijama-luna", false);

        assertThat(subject.bySlug("pijama-luna")).isEmpty();
    }

    @Test
    void givenAnUnknownOrMalformedSlug_whenLookingItUp_thenThereIsNoSuchProduct() {
        assertThat(subject.bySlug("pijama-nube")).isEmpty();
        assertThat(subject.bySlug("Pijama Nube!")).isEmpty();
    }
}
