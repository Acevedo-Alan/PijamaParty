package com.pijamaparty.store.ordering.application;

import java.util.Optional;
import java.util.UUID;

class InMemoryOrderCountsByCustomerContractTest
    extends OrderCountsByCustomerContractTest<InMemoryOrderCountsByCustomer> {

    @Override
    protected InMemoryOrderCountsByCustomer createSubject() {
        return new InMemoryOrderCountsByCustomer();
    }

    @Override
    protected void givenOrder(Optional<UUID> customer, String status) {
        subject.order(customer);
    }
}
