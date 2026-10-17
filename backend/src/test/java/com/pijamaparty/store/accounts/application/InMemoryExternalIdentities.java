package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.InMemoryStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fake of the read/write pair {@link FindExternalIdentities} and {@link LinkExternalIdentities}. */
public class InMemoryExternalIdentities implements FindExternalIdentities, LinkExternalIdentities {

    private final InMemoryStore<UUID, ExternalIdentity> identities = InMemoryStore.ofImmutable(ExternalIdentity::id);

    @Override
    public Optional<ExternalIdentity> find(ExternalAccount account) {
        return identities.findAll(identity -> identity.account().equals(account)).stream().findFirst();
    }

    @Override
    public boolean hasProvider(UserId user, ExternalProvider provider) {
        return !identities.findAll(identity -> identity.userId().equals(user)
            && identity.account().provider() == provider).isEmpty();
    }

    /** Synchronized: the real adapter lets the unique constraints pick one of two links. */
    @Override
    public synchronized boolean link(ExternalIdentity identity) {
        if (find(identity.account()).isPresent() || hasProvider(identity.userId(), identity.account().provider())) {
            return false;
        }
        identities.save(identity);
        return true;
    }

    public List<ExternalIdentity> all() {
        return identities.findAll();
    }
}
