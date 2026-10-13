package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.OutboxEmail;
import java.time.Instant;
import java.util.Optional;

/** Outbound read port (RNF-23): the next email due, locked until the caller's transaction, which must exist, ends. */
public interface LockDueEmails {

    /** The oldest pending email due at {@code now} that no one else is taking. Empty when none is due. */
    Optional<OutboxEmail> lockNextDue(Instant now);
}
