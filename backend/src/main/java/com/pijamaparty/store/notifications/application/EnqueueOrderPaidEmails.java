package com.pijamaparty.store.notifications.application;

import java.util.Objects;
import java.util.UUID;

/**
 * UC-17: a paid order gets its payment-confirmed emails, to the buyer and to the administrator, enqueued in the
 * transaction that paid it, which must exist (RNF-23). Runs as {@code SYSTEM}. Repeating it enqueues nothing more
 * (RNF-20.7).
 */
public interface EnqueueOrderPaidEmails {

    Result execute(Command command);

    record Command(UUID orderId) {

        public Command {
            Objects.requireNonNull(orderId, "orderId");
        }
    }

    /** @param enqueued how many emails were new, 0 when the order already had them */
    record Result(int enqueued) {}
}
