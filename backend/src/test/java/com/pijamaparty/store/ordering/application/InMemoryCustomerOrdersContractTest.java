package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;

class InMemoryCustomerOrdersContractTest extends CustomerOrdersContractTest<InMemoryOrderStore> {

    @Override
    protected InMemoryOrderStore createSubject() {
        return new InMemoryOrderStore();
    }

    @Override
    protected void shipped(OrderId order, String shipmentTrackingNumber) {
        subject.ship(order, shipmentTrackingNumber);
    }
}
