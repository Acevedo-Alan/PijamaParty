package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.VariantId;

class InMemoryVariantAvailabilityContractTest
    extends VariantAvailabilityLookupContractTest<InMemoryVariantAvailability> {

    @Override
    protected InMemoryVariantAvailability createSubject() {
        return new InMemoryVariantAvailability();
    }

    @Override
    protected void givenAvailable(VariantId variant, int units) {
        subject.setAvailable(variant, units);
    }
}
