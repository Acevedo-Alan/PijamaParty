package com.pijamaparty.store.payments.application;

class InMemoryPaymentStoreContractTest extends PaymentStoreContractTest<InMemoryPaymentStore> {

    @Override
    protected InMemoryPaymentStore createSubject() {
        return new InMemoryPaymentStore();
    }
}
