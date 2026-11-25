package com.pijamaparty.store.backoffice.application;

import java.util.UUID;

class InMemoryCatalogSetupFactsContractTest extends CatalogSetupFactsContractTest<InMemoryCatalogSetupFacts> {

    @Override
    protected InMemoryCatalogSetupFacts createSubject() {
        return new InMemoryCatalogSetupFacts();
    }

    @Override
    protected UUID givenCategory(String name, int position, boolean active, boolean sizeGuide) {
        return subject.category(name, position, active, sizeGuide);
    }

    @Override
    protected UUID givenProduct(UUID category, String name, boolean published, int minutesAgo, boolean photo) {
        return subject.product(category, name, published, minutesAgo, photo);
    }

    @Override
    protected void givenBanner(boolean published) {
        subject.banner(published);
    }
}
