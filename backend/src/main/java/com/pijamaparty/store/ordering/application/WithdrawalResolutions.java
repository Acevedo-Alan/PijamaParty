package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Outbound port (UC-29, T-E3-26): the administrator resolves a withdrawal request, in the caller's transaction. */
public interface WithdrawalResolutions {

    /** Locks the request until the transaction ends. Empty when there is no such request. */
    Optional<LockedWithdrawal> lockForResolution(UUID request);

    /** Sets a locked {@code RECEIVED} request {@code RESOLVED} at {@code at}, which is not before its creation. */
    void resolve(UUID request, Instant at);

    /** @param resolvedAt present exactly when {@code RESOLVED} */
    record LockedWithdrawal(WithdrawalStatus status, Instant createdAt, Optional<Instant> resolvedAt) {

        public LockedWithdrawal {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(resolvedAt, "resolvedAt");
        }
    }
}
