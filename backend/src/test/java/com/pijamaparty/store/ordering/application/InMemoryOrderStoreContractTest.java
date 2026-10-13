package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;
import java.util.Optional;

class InMemoryOrderStoreContractTest extends OrderStoreContractTest<InMemoryOrderStore> {

    @Override
    protected InMemoryOrderStore createSubject() {
        return new InMemoryOrderStore();
    }

    @Override
    protected void givenOrder(OrderId order, OrderStatus status, Instant reservationExpiresAt) {
        subject.add(order, status, reservationExpiresAt);
    }

    @Override
    protected Optional<OrderStatus> statusOf(OrderId order) {
        return subject.statusOf(order);
    }
}
