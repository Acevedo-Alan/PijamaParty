package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

class InMemoryCustomerPurchaseFiguresContractTest
    extends CustomerPurchaseFiguresContractTest<InMemoryCustomerPurchaseFigures> {

    @Override
    protected InMemoryCustomerPurchaseFigures createSubject() {
        return new InMemoryCustomerPurchaseFigures();
    }

    @Override
    protected void givenOrder(Optional<UUID> customer, String status, long totalCents, Instant at) {
        subject.order(customer, OrderStatus.valueOf(status), totalCents, at);
    }
}
