package com.pijamaparty.store.accounts.application;

class InMemoryExternalSignInsContractTest extends ExternalSignInsContractTest<InMemoryExternalSignIns> {

    @Override
    protected InMemoryExternalSignIns createSubject() {
        return new InMemoryExternalSignIns();
    }
}
