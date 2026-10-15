package com.pijamaparty.store.ordering.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-29 (T-E3-26): the active withdrawal request of an order, for the back-office order detail. Read in its own
 * transaction, or the caller's, as the caller: the administrator, whom Row Level Security lets read every request.
 */
public interface OrderWithdrawals {

    /** Empty when the order has no {@code RECEIVED} request, or Row Level Security hides it from the actor. */
    Optional<ActiveWithdrawal> activeOf(UUID orderId);

    /** Not personal data. */
    record ActiveWithdrawal(UUID requestId, String code, boolean outOfTerm, Instant requestedAt) {

        public ActiveWithdrawal {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(requestedAt, "requestedAt");
        }
    }
}
