package com.pijamaparty.store.ordering.application;

class InMemoryPaidOrderDetailsContractTest extends PaidOrderDetailsContractTest<InMemoryOrderStore> {

    @Override
    protected InMemoryOrderStore createSubject() {
        return new InMemoryOrderStore();
    }
}
