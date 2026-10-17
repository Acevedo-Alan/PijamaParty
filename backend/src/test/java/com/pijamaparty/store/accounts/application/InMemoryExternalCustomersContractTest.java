package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;

class InMemoryExternalCustomersContractTest extends ExternalCustomersContractTest<InMemoryUserStore> {

    @Override
    protected InMemoryUserStore createSubject() {
        return new InMemoryUserStore();
    }

    @Override
    protected void arrange(User user) {
        subject.save(user);
    }
}
