package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.platform.domain.UserId;

/** Outbound write port: the external accounts waiting for their link confirmation email (T-E4-14, V35). */
public interface SavePendingLinks {

    /**
     * Keeps {@code link}, replacing the one of its customer, if any. Whether it may replace it is the use case's
     * decision ({@link PendingLink#yieldsTo}), under {@link LoadPendingLinks#lockByCustomer}.
     */
    void request(PendingLink link);

    /** Forgets the pending link of {@code user}, if any. */
    void forget(UserId user);
}
