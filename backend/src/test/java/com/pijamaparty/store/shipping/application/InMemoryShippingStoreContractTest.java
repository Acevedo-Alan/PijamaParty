package com.pijamaparty.store.shipping.application;

class InMemoryShippingStoreContractTest extends ShippingStoreContractTest<InMemoryShippingStore> {

    @Override
    protected InMemoryShippingStore createSubject() {
        return new InMemoryShippingStore();
    }
}
