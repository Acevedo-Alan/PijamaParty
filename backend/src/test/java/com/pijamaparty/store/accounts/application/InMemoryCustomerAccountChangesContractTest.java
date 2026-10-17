package com.pijamaparty.store.accounts.application;

class InMemoryCustomerAccountChangesContractTest
    extends CustomerAccountChangesContractTest<InMemoryCustomerAccountChanges> {

    @Override
    protected InMemoryCustomerAccountChanges createSubject() {
        return new InMemoryCustomerAccountChanges();
    }
}
