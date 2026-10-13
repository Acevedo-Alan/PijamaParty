package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.UUID;

class InMemoryStockRestocksContractTest extends StockRestocksContractTest<InMemoryStockReleases> {

    @Override
    protected InMemoryStockReleases createSubject() {
        return new InMemoryStockReleases();
    }

    @Override
    protected void givenDeducted(OrderId order, UUID variantId, int onHand, int deducted) {
        subject.reserved(order, variantId, onHand, deducted);
        subject.deduct(order);
    }

    @Override
    protected void givenReleased(OrderId order, UUID variantId, int onHand, int reserved) {
        subject.reserved(order, variantId, onHand, reserved);
        subject.release(order);
    }

    @Override
    protected int onHandOf(UUID variantId) {
        return subject.onHandOf(variantId);
    }
}
