package com.pijamaparty.store.accounts.application;

class InMemoryPasswordHasherContractTest extends PasswordHasherContractTest<InMemoryPasswordHasher> {

    @Override
    protected InMemoryPasswordHasher createSubject() {
        return new InMemoryPasswordHasher();
    }
}
