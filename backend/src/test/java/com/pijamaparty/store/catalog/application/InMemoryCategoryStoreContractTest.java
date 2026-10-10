package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;

class InMemoryCategoryStoreContractTest extends CategoryStoreContractTest<InMemoryCategoryStore> {

    @Override
    protected InMemoryCategoryStore createSubject() {
        return new InMemoryCategoryStore();
    }

    @Override
    protected void givenAProductIn(CategoryId category) {
        subject.addProductTo(category);
    }
}
