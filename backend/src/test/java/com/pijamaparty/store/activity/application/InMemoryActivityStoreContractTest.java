package com.pijamaparty.store.activity.application;

class InMemoryActivityStoreContractTest extends ActivityStoreContractTest<InMemoryActivityStore> {

    @Override
    protected InMemoryActivityStore createSubject() {
        return new InMemoryActivityStore();
    }
}
