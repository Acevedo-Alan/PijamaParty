package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.UUID;

class InMemoryVerifiedCustomerEmailsContractTest
    extends VerifiedCustomerEmailsContractTest<InMemoryVerifiedCustomerEmails> {

    @Override
    protected InMemoryVerifiedCustomerEmails createSubject() {
        return new InMemoryVerifiedCustomerEmails();
    }

    @Override
    protected UserId givenCustomer(String email, boolean verified) {
        UserId customer = UserId.of(UUID.randomUUID());
        if (verified) {
            subject.verified(customer, email);
        }
        return customer;
    }
}
