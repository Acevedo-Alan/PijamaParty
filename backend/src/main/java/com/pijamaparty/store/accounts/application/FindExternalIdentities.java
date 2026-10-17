package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

/** Outbound read port: the external accounts linked to customers (UC-27), in the caller's transaction. */
public interface FindExternalIdentities {

    Optional<ExternalIdentity> find(ExternalAccount account);

    /** Whether {@code user} has an account of {@code provider} linked: at most one of each (V34). */
    boolean hasProvider(UserId user, ExternalProvider provider);
}
