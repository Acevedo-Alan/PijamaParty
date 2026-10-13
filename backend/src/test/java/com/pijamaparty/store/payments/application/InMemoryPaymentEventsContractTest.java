package com.pijamaparty.store.payments.application;

import java.util.List;

class InMemoryPaymentEventsContractTest extends PaymentEventsContractTest<InMemoryPaymentEvents> {

    @Override
    protected InMemoryPaymentEvents createSubject() {
        return new InMemoryPaymentEvents();
    }

    @Override
    protected List<Object> published() {
        return subject.published();
    }
}
