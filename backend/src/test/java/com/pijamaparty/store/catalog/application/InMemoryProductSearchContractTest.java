package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;

class InMemoryProductSearchContractTest extends ProductSearchContractTest<InMemoryProductSearch> {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryProductStore products = new InMemoryProductStore(categories);
    private final InMemoryProductListing listing = new InMemoryProductListing(categories, products);

    @Override
    protected InMemoryProductSearch createSubject() {
        return new InMemoryProductSearch(categories, products, listing);
    }

    @Override
    protected void givenCategory(Category category) {
        categories.save(category);
    }

    @Override
    protected void givenCategoryUpdated(Category category) {
        categories.save(category);
    }

    @Override
    protected void givenProduct(Product product) {
        products.save(product);
    }

    @Override
    protected void givenAvailable(VariantId variant, int available) {
        listing.setAvailable(variant, available);
    }
}
