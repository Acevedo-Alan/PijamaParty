package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.User;

class InMemoryDeleteCustomersContractTest extends DeleteCustomersContractTest<InMemorySignInMethods> {

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryExternalIdentities identities = new InMemoryExternalIdentities();

    @Override
    protected InMemorySignInMethods createSubject() {
        return new InMemorySignInMethods(users, identities);
    }

    @Override
    protected void arrange(User user) {
        users.save(user);
    }

    @Override
    protected void arrange(ExternalIdentity identity) {
        identities.link(identity);
    }
}
