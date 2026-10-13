package com.pijamaparty.store.ordering.application;

class InMemoryAdminOrdersContractTest extends AdminOrdersContractTest<InMemoryOrderStore> {

    @Override
    protected InMemoryOrderStore createSubject() {
        return new InMemoryOrderStore();
    }
}
