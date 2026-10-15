package com.pijamaparty.store.notifications.application;

import java.util.Objects;
import java.util.UUID;

/**
 * UC-17 (T-E3-23): the buyer corrected the email of a paid order, so its confirmation goes again, to the new email.
 * Enqueued in the correction's transaction, which must exist, as the buyer. The administrator's notice is not sent
 * again.
 */
public interface EnqueueCorrectedOrderEmail {

    Result execute(Command command);

    record Command(UUID orderId) {

        public Command {
            Objects.requireNonNull(orderId, "orderId");
        }
    }

    sealed interface Result {

        /** A new version of the buyer's confirmation is due at once. */
        record Enqueued(int version) implements Result {}

        /** The buyer's confirmation has not been attempted yet: when sent, it is composed with the new email. */
        record PendingTakesIt() implements Result {}
    }
}
