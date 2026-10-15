package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Outbound write port (UC-29, T-E3-25): records a withdrawal request, {@code RECEIVED}, in the caller's transaction. */
public interface SaveWithdrawals {

    /** @return false when another request already has this code, and nothing was inserted; the caller draws another */
    boolean insert(NewWithdrawal withdrawal);

    record NewWithdrawal(UUID id, OrderId order, WithdrawalCode code, boolean outOfTerm, Instant createdAt) {

        public NewWithdrawal {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }
}
