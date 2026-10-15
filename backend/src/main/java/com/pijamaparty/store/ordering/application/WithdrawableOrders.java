package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound read port (UC-29, T-E3-25): locks the order of a number, when its email is the one given, until the
 * caller's transaction ends, so two withdrawal requests for it run one after the other. As the guest or the customer
 * that sends the form: from then on, in that transaction, it reaches that order's requests (RNF-27).
 */
public interface WithdrawableOrders {

    /**
     * @param email normalized ({@code BuyerEmail}); personal data, never logged (R-05)
     * @return empty when no order has this number, or its email is another one: the same for both
     */
    Optional<WithdrawableOrder> lock(long number, String email);

    record WithdrawableOrder(OrderId id, long number, OrderStatus status, Instant createdAt,
                             Optional<Instant> deliveredAt) {

        public WithdrawableOrder {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(deliveredAt, "deliveredAt");
        }
    }
}
