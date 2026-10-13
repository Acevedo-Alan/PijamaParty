package com.pijamaparty.store.ordering.application;

class InMemoryOpenPaymentsContractTest extends OpenPaymentsContractTest<InMemoryOpenPayments> {

    @Override
    protected InMemoryOpenPayments createSubject() {
        return new InMemoryOpenPayments();
    }

    @Override
    protected void providerIsDown() {
        subject.down(true);
    }

    @Override
    protected void providerIsBack() {
        subject.down(false);
    }
}
