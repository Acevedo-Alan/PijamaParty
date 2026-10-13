package com.pijamaparty.store.notifications.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound write port (RNF-23): what becomes of a pending email, in the caller's transaction or, when there is none,
 * in its own short one. A call about an email no longer pending changes nothing.
 */
public interface RecordEmailOutcomes {

    /** An attempt begins: the email has begun {@code attempts} and is due again at {@code retryAt}. */
    void attempting(UUID email, int attempts, Instant retryAt, Instant at);

    /** The provider accepted the pending email: {@code SENT}, with the provider's id of it (T-E3-22). */
    void sent(UUID email, String providerMessageId, Instant at);

    /** The pending email will not be sent: {@code FAILED}. */
    void failed(UUID email, Instant at);
}
