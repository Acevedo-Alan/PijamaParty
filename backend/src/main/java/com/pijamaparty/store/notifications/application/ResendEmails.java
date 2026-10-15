package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound write port (UC-17, T-E3-23): sends an email of the outbox again, as a new version, in the caller's
 * transaction, which must exist.
 */
public interface ResendEmails {

    /**
     * Locks, until the caller's transaction ends, an email of this type and reference that is pending and has not
     * begun any attempt, so the job cannot take it meanwhile. Waits for one the job is claiming right now.
     *
     * @return false when there is none
     */
    boolean lockUnattempted(EmailType type, UUID reference);

    /**
     * Enqueues the email, due at once, as the version after the latest of this type and reference (1 when there is
     * none).
     *
     * @return the new version
     */
    int enqueueNextVersion(EmailType type, UUID reference, Instant at);
}
