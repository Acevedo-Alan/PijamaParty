package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;

/** The fake keeps no users: any customer can be linked. */
class InMemoryExternalIdentitiesContractTest extends ExternalIdentityStoreContractTest<InMemoryExternalIdentities> {

    @Override
    protected InMemoryExternalIdentities createSubject() {
        return new InMemoryExternalIdentities();
    }

    @Override
    protected void arrange(User user) {
    }
}
