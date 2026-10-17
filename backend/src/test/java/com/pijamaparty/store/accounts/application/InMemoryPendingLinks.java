package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.InMemoryStore;
import java.util.Optional;

/** Fake of the read/write pair {@link LoadPendingLinks} and {@link SavePendingLinks}. */
public class InMemoryPendingLinks implements LoadPendingLinks, SavePendingLinks {

    private final InMemoryStore<UserId, PendingLink> links = InMemoryStore.ofImmutable(PendingLink::userId);

    @Override
    public Optional<PendingLink> find(UserId user) {
        return links.findById(user);
    }

    /** One thread at a time in the use-case tests: nothing to lock. */
    @Override
    public Optional<PendingLink> lockByCustomer(UserId user) {
        return links.findById(user);
    }

    @Override
    public void request(PendingLink link) {
        links.save(link);
    }

    @Override
    public void forget(UserId user) {
        links.delete(user);
    }
}
