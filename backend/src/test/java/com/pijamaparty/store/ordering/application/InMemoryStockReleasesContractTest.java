package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.UUID;

class InMemoryStockReleasesContractTest extends StockReleasesContractTest<InMemoryStockReleases> {

    @Override
    protected InMemoryStockReleases createSubject() {
        return new InMemoryStockReleases();
    }

    @Override
    protected void givenReserved(OrderId order, UUID variantId, int onHand, int reserved) {
        subject.reserved(order, variantId, onHand, reserved);
    }

    @Override
    protected int availableOf(UUID variantId) {
        return subject.availableOf(variantId);
    }
}
