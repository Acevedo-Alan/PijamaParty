package com.pijamaparty.store.inventory.application;

import java.util.Optional;
import java.util.UUID;

class InMemoryAddStockLevelsContractTest extends AddStockLevelsContractTest<InMemoryAddStockLevels> {

    @Override
    protected InMemoryAddStockLevels createSubject() {
        return new InMemoryAddStockLevels();
    }

    @Override
    protected Optional<Level> levelOf(UUID variantId) {
        return subject.levelOf(variantId);
    }

    @Override
    protected void givenOnHand(UUID variantId, int quantity) {
        subject.setOnHand(variantId, quantity);
    }
}
