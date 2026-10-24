package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.util.List;

class InMemoryCatalogActivityContractTest extends CatalogActivityContractTest<InMemoryCatalogActivity> {

    @Override
    protected InMemoryCatalogActivity createSubject() {
        return new InMemoryCatalogActivity();
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return subject.entries();
    }
}
