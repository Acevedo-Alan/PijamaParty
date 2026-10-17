package com.pijamaparty.store.notifications.application;

import java.util.Objects;
import java.util.UUID;

/**
 * UC-27 (T-E4-14): someone typed a customer's email to connect an external account; nothing was linked, and the
 * customer is told. Enqueued in the transaction of the request, as {@code SYSTEM}. One still waiting to be sent is
 * enough: it says the same.
 */
public interface EnqueueLinkAttemptNotice {

    Result execute(Command command);

    record Command(UUID userId) {

        public Command {
            Objects.requireNonNull(userId, "userId");
        }
    }

    sealed interface Result {

        /** A new version of the notice is due at once. */
        record Enqueued(int version) implements Result {}

        /** One has not been attempted yet: it tells the same. */
        record PendingTakesIt() implements Result {}
    }
}
