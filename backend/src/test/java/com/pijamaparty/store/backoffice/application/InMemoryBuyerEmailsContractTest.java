package com.pijamaparty.store.backoffice.application;

import java.util.UUID;

class InMemoryBuyerEmailsContractTest extends BuyerEmailsContractTest<InMemoryBuyerEmails> {

    @Override
    protected InMemoryBuyerEmails createSubject() {
        return new InMemoryBuyerEmails();
    }

    @Override
    protected void givenFailed(UUID orderId) {
        subject.failed(orderId);
    }

    @Override
    protected void givenBounced(UUID orderId) {
        subject.bounced(orderId);
    }

    @Override
    protected void givenPending(UUID orderId) {
    }
}
