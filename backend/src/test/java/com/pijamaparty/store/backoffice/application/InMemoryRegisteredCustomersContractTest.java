package com.pijamaparty.store.backoffice.application;

import java.time.Instant;
import java.util.UUID;

class InMemoryRegisteredCustomersContractTest extends RegisteredCustomersContractTest<InMemoryRegisteredCustomers> {

    @Override
    protected InMemoryRegisteredCustomers createSubject() {
        return new InMemoryRegisteredCustomers();
    }

    @Override
    protected UUID givenCustomer(String name, String email, Instant registeredAt) {
        return subject.add(name, email, registeredAt);
    }
}
