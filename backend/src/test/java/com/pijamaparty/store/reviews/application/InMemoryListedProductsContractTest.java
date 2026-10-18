package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Set;

class InMemoryListedProductsContractTest extends ListedProductsContractTest<InMemoryCatalogProducts> {

    @Override
    protected InMemoryCatalogProducts createSubject() {
        return new InMemoryCatalogProducts();
    }

    @Override
    protected void givenProduct(ProductId product, String slug, boolean listed) {
        subject.add(product, "Pijama Luna", Set.of());
        if (listed) {
            subject.list(product, slug);
        }
    }
}
