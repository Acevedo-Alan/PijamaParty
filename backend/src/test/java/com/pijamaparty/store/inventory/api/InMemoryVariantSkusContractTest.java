package com.pijamaparty.store.inventory.api;

import java.util.UUID;

class InMemoryVariantSkusContractTest extends VariantSkusContractTest<InMemoryVariantSkus> {

    @Override
    protected InMemoryVariantSkus createSubject() {
        return new InMemoryVariantSkus();
    }

    @Override
    protected Given givenVariant() {
        Given given = new Given(UUID.randomUUID(), "PP-AB12CD34");
        subject.add(given.variant(), given.sku());
        return given;
    }
}
