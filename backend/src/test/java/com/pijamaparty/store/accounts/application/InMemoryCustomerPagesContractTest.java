package com.pijamaparty.store.accounts.application;

import java.time.Instant;
import java.util.UUID;

class InMemoryCustomerPagesContractTest extends CustomerPagesContractTest<InMemoryCustomerPages> {

    @Override
    protected InMemoryCustomerPages createSubject() {
        return new InMemoryCustomerPages();
    }

    @Override
    protected void givenUser(UUID id, String name, String email, String role, Instant registeredAt) {
        subject.add(id, name, email, role, registeredAt);
    }
}
