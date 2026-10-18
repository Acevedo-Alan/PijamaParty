package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.UUID;

class InMemoryCustomerNamesContractTest extends CustomerNamesContractTest<InMemoryCustomerNames> {

    @Override
    protected InMemoryCustomerNames createSubject() {
        return new InMemoryCustomerNames();
    }

    @Override
    protected UserId givenCustomer(String name) {
        UserId customer = UserId.of(UUID.randomUUID());
        subject.add(customer, name);
        return customer;
    }
}
