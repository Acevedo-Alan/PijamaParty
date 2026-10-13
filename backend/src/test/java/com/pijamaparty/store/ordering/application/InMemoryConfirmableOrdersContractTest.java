package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.util.Optional;
import java.util.UUID;

class InMemoryConfirmableOrdersContractTest extends ConfirmableOrdersContractTest<InMemoryOrderStore> {

    @Override
    protected InMemoryOrderStore createSubject() {
        return new InMemoryOrderStore();
    }

    @Override
    protected long givenOrder(OrderId order, OrderStatus status, Optional<UUID> couponId) {
        return subject.add(order, status, EXPIRES, couponId);
    }
}
