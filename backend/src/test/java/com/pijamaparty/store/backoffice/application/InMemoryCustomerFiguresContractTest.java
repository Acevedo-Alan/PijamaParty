package com.pijamaparty.store.backoffice.application;

import java.time.Instant;

class InMemoryCustomerFiguresContractTest extends CustomerFiguresContractTest<InMemoryCustomerFigures> {

    @Override
    protected InMemoryCustomerFigures createSubject() {
        return new InMemoryCustomerFigures();
    }

    @Override
    protected void givenCustomer(Instant registeredAt) {
        subject.registered(registeredAt);
    }

    @Override
    protected void givenAdministrator(Instant registeredAt) {
        // The fake holds customers only, as Row Level Security and the role condition leave the administrator out.
    }
}
