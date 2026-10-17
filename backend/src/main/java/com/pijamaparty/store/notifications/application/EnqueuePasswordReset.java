package com.pijamaparty.store.notifications.application;

import java.util.Objects;
import java.util.UUID;

/**
 * UC-19 (T-E4-03): a customer asked to recover its password. Enqueued in that transaction, which must exist, as
 * {@code SYSTEM}. One still waiting to be sent is enough: its link is minted when it is sent.
 */
public interface EnqueuePasswordReset {

    Result execute(Command command);

    record Command(UUID userId) {

        public Command {
            Objects.requireNonNull(userId, "userId");
        }
    }

    sealed interface Result {

        /** A new version of the reset email is due at once. */
        record Enqueued(int version) implements Result {}

        /** One has not been attempted yet: it carries a fresh link when sent. */
        record PendingTakesIt() implements Result {}
    }
}
