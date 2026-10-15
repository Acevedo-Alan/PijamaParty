package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-29 (T-E3-26): a withdrawal request as a row of the back-office list, with its order. Personal data (R-05):
 * {@code toString()} hides the buyer.
 *
 * @param resolvedAt present exactly when {@code RESOLVED}
 */
public record WithdrawalSummary(UUID id, WithdrawalCode code, OrderId orderId, long orderNumber,
                                OrderStatus orderStatus, String buyerName, Instant createdAt, boolean outOfTerm,
                                WithdrawalStatus status, Optional<Instant> resolvedAt) {

    public WithdrawalSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderStatus, "orderStatus");
        Objects.requireNonNull(buyerName, "buyerName");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
    }

    @Override
    public String toString() {
        return "WithdrawalSummary[code=" + code.value() + ", orderNumber=" + orderNumber + ", status=" + status
            + ", ***]";
    }
}
