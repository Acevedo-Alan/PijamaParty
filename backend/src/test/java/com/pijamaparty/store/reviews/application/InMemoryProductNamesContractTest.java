package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Set;
import java.util.UUID;

class InMemoryProductNamesContractTest extends ProductNamesContractTest<InMemoryCatalogProducts> {

    @Override
    protected InMemoryCatalogProducts createSubject() {
        return new InMemoryCatalogProducts();
    }

    @Override
    protected void givenProduct(ProductId product, String name, boolean published) {
        subject.add(product, name, Set.of(UUID.randomUUID()));
    }
}
