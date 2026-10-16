package com.pijamaparty.store.notifications.application;

import java.util.Objects;
import java.util.UUID;

/**
 * UC-19 (T-E4-02): a customer needs a verification email, because it registered or because it signed in before
 * verifying. Enqueued in that transaction, which must exist, as {@code SYSTEM}. One still waiting to be sent is
 * enough: its link is minted when it is sent.
 */
public interface EnqueueEmailVerification {

    Result execute(Command command);

    record Command(UUID userId) {

        public Command {
            Objects.requireNonNull(userId, "userId");
        }
    }

    sealed interface Result {

        /** A new version of the verification email is due at once. */
        record Enqueued(int version) implements Result {}

        /** One has not been attempted yet: it carries a fresh link when sent. */
        record PendingTakesIt() implements Result {}
    }
}
