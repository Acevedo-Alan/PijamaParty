package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.util.Optional;

class InMemoryNewOrdersContractTest extends NewOrdersContractTest<InMemoryOrderStore> {

    @Override
    protected InMemoryOrderStore createSubject() {
        return new InMemoryOrderStore();
    }

    @Override
    protected Optional<SaveOrders.NewOrder> storedOrder(OrderId order) {
        return subject.placed(order);
    }

    @Override
    protected Optional<OrderStatus> statusOf(OrderId order) {
        return subject.statusOf(order);
    }
}
