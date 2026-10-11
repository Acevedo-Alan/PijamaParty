package com.pijamaparty.store.catalog.application;

import java.util.List;

class InMemoryCatalogEventsContractTest extends CatalogEventsContractTest<InMemoryCatalogEvents> {

    @Override
    protected InMemoryCatalogEvents createSubject() {
        return new InMemoryCatalogEvents();
    }

    @Override
    protected List<Object> published() {
        return subject.published();
    }
}
