package com.pijamaparty.store.backoffice.application;

import java.util.UUID;

class InMemoryCatalogFiguresContractTest extends CatalogFiguresContractTest<InMemoryCatalogFigures> {

    @Override
    protected InMemoryCatalogFigures createSubject() {
        return new InMemoryCatalogFigures();
    }

    @Override
    protected UUID givenProduct(String name, boolean published) {
        return subject.product(name, published);
    }

    @Override
    protected UUID givenVariant(UUID product, String size, int position, boolean active, int available) {
        return subject.variant(product, size, position, active, available);
    }
}
