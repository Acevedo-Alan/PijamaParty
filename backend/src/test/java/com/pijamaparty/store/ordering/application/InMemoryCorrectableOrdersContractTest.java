package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;

class InMemoryCorrectableOrdersContractTest extends CorrectableOrdersContractTest<InMemoryOrderStore> {

    @Override
    protected InMemoryOrderStore createSubject() {
        return new InMemoryOrderStore();
    }

    @Override
    protected void shipped(OrderId order) {
        subject.ship(order, "CA123456789AR");
    }
}
