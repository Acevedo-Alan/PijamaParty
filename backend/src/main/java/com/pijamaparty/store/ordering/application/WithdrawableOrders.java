package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound read port (UC-29, T-E3-25): locks the order of a number until the caller's transaction ends, so two
 * withdrawal requests for it run one after the other. As the system.
 */
public interface WithdrawableOrders {

    /** Empty when no order has this number. */
    Optional<WithdrawableOrder> lock(long number);

    /** @param email the buyer's, normalized; personal data (R-05), so {@link #toString()} hides it */
    record WithdrawableOrder(OrderId id, long number, String email, OrderStatus status, Instant createdAt,
                             Optional<Instant> deliveredAt) {

        public WithdrawableOrder {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(deliveredAt, "deliveredAt");
        }

        @Override
        public String toString() {
            return "WithdrawableOrder[id=" + id + ", number=" + number + ", email=***, status=" + status + "]";
        }
    }
}
