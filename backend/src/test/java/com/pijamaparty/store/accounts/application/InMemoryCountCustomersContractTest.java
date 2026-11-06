package com.pijamaparty.store.accounts.application;

import java.time.Instant;

class InMemoryCountCustomersContractTest extends CountCustomersContractTest<InMemoryCountCustomers> {

    @Override
    protected InMemoryCountCustomers createSubject() {
        return new InMemoryCountCustomers();
    }

    @Override
    protected void givenUser(String role, Instant registeredAt) {
        subject.add(role, registeredAt);
    }
}
