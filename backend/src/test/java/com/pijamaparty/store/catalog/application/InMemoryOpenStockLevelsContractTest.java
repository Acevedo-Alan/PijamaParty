package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.Optional;

class InMemoryOpenStockLevelsContractTest extends OpenStockLevelsContractTest<InMemoryOpenStockLevels> {

    @Override
    protected InMemoryOpenStockLevels createSubject() {
        return new InMemoryOpenStockLevels();
    }

    @Override
    protected Optional<Integer> onHandOf(VariantId variant) {
        return subject.onHandOf(variant);
    }

    @Override
    protected void givenOnHand(VariantId variant, int quantity) {
        subject.setOnHand(variant, quantity);
    }
}
