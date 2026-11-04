package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;

class InMemoryStockOverviewPagesContractTest extends StockOverviewPagesContractTest<InMemoryStockOverviewPages> {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryProductStore products = new InMemoryProductStore(categories);

    @Override
    protected InMemoryStockOverviewPages createSubject() {
        return new InMemoryStockOverviewPages(products);
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
        subject.setOnHand(variant, available);
    }

    @Override
    protected void givenReserved(VariantId variant, int reserved) {
        subject.setReserved(variant, reserved);
    }
}
