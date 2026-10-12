package com.pijamaparty.store.cart.application;

class InMemoryCartStoreContractTest extends CartStoreContractTest<InMemoryCartStore> {

    @Override
    protected InMemoryCartStore createSubject() {
        return new InMemoryCartStore();
    }
}
