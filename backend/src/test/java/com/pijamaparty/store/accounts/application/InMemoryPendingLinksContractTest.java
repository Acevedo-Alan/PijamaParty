package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;

/** The fake keeps no users: any customer can wait for a link. */
class InMemoryPendingLinksContractTest extends PendingLinkStoreContractTest<InMemoryPendingLinks> {

    @Override
    protected InMemoryPendingLinks createSubject() {
        return new InMemoryPendingLinks();
    }

    @Override
    protected void arrange(User user) {
    }
}
