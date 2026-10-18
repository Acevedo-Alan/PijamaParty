package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Set;
import java.util.UUID;

class InMemoryProductLinksContractTest extends ProductLinksContractTest<InMemoryCatalogProducts> {

    @Override
    protected InMemoryCatalogProducts createSubject() {
        return new InMemoryCatalogProducts();
    }

    @Override
    protected void givenProduct(ProductId product, String name, String slug, boolean published) {
        subject.add(product, name, slug, Set.of(UUID.randomUUID()));
    }
}
