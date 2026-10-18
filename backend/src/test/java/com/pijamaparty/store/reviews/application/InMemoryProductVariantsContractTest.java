package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Set;
import java.util.UUID;

class InMemoryProductVariantsContractTest extends ProductVariantsContractTest<InMemoryCatalogProducts> {

    @Override
    protected InMemoryCatalogProducts createSubject() {
        return new InMemoryCatalogProducts();
    }

    @Override
    protected void givenProduct(ProductId product, boolean published, UUID soldVariant, UUID unsoldVariant) {
        subject.add(product, "Pijama Luna", Set.of(soldVariant, unsoldVariant));
    }
}
