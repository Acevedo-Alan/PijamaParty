package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

/** Outbound read port: the external account waiting for a customer's link confirmation email (T-E4-14, V35). */
public interface LoadPendingLinks {

    Optional<PendingLink> find(UserId user);

    /**
     * As {@link #find}, after locking the customer's account until the caller's transaction ends, so two requests for
     * one customer decide one after the other which external account waits (the first request wins, UC-27). The
     * account is the lock: a pending link has no row to lock before the first one exists.
     */
    Optional<PendingLink> lockByCustomer(UserId user);
}
