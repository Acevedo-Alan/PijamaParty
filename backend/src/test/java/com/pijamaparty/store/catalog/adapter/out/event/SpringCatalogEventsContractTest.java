package com.pijamaparty.store.catalog.adapter.out.event;

import com.pijamaparty.store.catalog.application.CatalogEventsContractTest;
import java.util.ArrayList;
import java.util.List;

/** Plain JUnit: the publisher Spring would inject only records what it gets. */
class SpringCatalogEventsContractTest extends CatalogEventsContractTest<SpringCatalogEvents> {

    private final List<Object> published = new ArrayList<>();

    @Override
    protected SpringCatalogEvents createSubject() {
        return new SpringCatalogEvents(published::add);
    }

    @Override
    protected List<Object> published() {
        return published;
    }
}
