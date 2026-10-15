package com.pijamaparty.store.notifications.application;

import java.util.Objects;
import java.util.UUID;

/**
 * UC-29 (T-E3-25): a withdrawal request gets its emails, the procedure code to the buyer and the notice to the
 * administrator, enqueued in the transaction that recorded it, which must exist. Runs as {@code SYSTEM}. Repeating
 * it enqueues nothing more (RNF-20.7).
 */
public interface EnqueueWithdrawalEmails {

    Result execute(Command command);

    record Command(UUID requestId) {

        public Command {
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    /** @param enqueued how many emails were new, 0 when the request already had them */
    record Result(int enqueued) {}
}
