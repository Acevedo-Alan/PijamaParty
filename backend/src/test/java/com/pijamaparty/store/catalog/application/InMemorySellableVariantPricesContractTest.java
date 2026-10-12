package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;

class InMemorySellableVariantPricesContractTest
    extends SellableVariantPricesContractTest<InMemorySellableVariantPrices> {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryProductStore products = new InMemoryProductStore(categories);

    @Override
    protected InMemorySellableVariantPrices createSubject() {
        return new InMemorySellableVariantPrices(categories, products);
    }

    @Override
    protected void givenCategory(Category category) {
        categories.save(category);
    }

    @Override
    protected void givenProduct(Product product) {
        products.save(product);
    }

    /** Prices do not depend on stock. */
    @Override
    protected void givenAvailable(VariantId variant, int available) {
    }
}
