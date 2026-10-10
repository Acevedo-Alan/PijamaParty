package com.pijamaparty.store.accounts.application;

class InMemoryUserStoreContractTest extends UserStoreContractTest<InMemoryUserStore> {

    @Override
    protected InMemoryUserStore createSubject() {
        return new InMemoryUserStore();
    }
}
