package com.pijamaparty.store.notifications.application;

import java.util.Objects;
import java.util.UUID;

/**
 * UC-27 (T-E4-14): a customer needs the email that confirms the link of an external account whose email the provider
 * does not vouch for. Enqueued in the transaction that keeps the pending link, which must exist, as {@code SYSTEM}.
 * One still waiting to be sent is enough: its link is minted when it is sent, from the latest pending link.
 */
public interface EnqueueLinkConfirmation {

    Result execute(Command command);

    record Command(UUID userId) {

        public Command {
            Objects.requireNonNull(userId, "userId");
        }
    }

    sealed interface Result {

        /** A new version of the link confirmation email is due at once. */
        record Enqueued(int version) implements Result {}

        /** One has not been attempted yet: it carries a fresh link when sent. */
        record PendingTakesIt() implements Result {}
    }
}
