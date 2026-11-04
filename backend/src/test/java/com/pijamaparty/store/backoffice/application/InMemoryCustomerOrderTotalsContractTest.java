package com.pijamaparty.store.backoffice.application;

import java.util.Optional;
import java.util.UUID;

class InMemoryCustomerOrderTotalsContractTest extends CustomerOrderTotalsContractTest<InMemoryCustomerOrderTotals> {

    @Override
    protected InMemoryCustomerOrderTotals createSubject() {
        return new InMemoryCustomerOrderTotals();
    }

    @Override
    protected void givenOrder(Optional<UUID> customer, String status) {
        subject.order(customer);
    }
}
