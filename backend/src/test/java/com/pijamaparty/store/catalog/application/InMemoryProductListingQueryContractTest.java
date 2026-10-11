package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;

class InMemoryProductListingQueryContractTest extends ProductListingQueryContractTest<InMemoryProductListing> {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryProductStore products = new InMemoryProductStore(categories);

    @Override
    protected InMemoryProductListing createSubject() {
        return new InMemoryProductListing(categories, products);
    }

    @Override
    protected void givenCategory(Category category) {
        categories.save(category);
    }

    @Override
    protected void givenProduct(Product product) {
        products.save(product);
    }

    @Override
    protected void givenAvailable(VariantId variant, int available) {
        subject.setAvailable(variant, available);
    }
}
