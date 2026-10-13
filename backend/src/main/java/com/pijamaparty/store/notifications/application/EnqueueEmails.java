package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Instant;
import java.util.UUID;

/** Outbound write port (RNF-23): puts an email in the outbox, in the caller's transaction, which must exist. */
public interface EnqueueEmails {

    /**
     * Enqueues the email, due at once, unless the outbox already has one of this type and reference (RNF-20.7).
     *
     * @return false when it was already there
     */
    boolean enqueue(EmailType type, UUID reference, Instant at);
}
