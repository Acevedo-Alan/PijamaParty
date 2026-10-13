package com.pijamaparty.store.ordering.application;

import java.util.UUID;

class InMemoryReserveOrderStockContractTest extends ReserveOrderStockContractTest<InMemoryReserveOrderStock> {

    @Override
    protected InMemoryReserveOrderStock createSubject() {
        return new InMemoryReserveOrderStock();
    }

    @Override
    protected void givenStock(UUID variantId, int onHand) {
        subject.stock(variantId, onHand);
    }

    @Override
    protected int availableOf(UUID variantId) {
        return subject.availableOf(variantId);
    }
}
