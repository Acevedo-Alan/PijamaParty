package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Outbound read port (UC-15): the order an approved payment is for, within the caller's transaction. */
public interface ConfirmableOrders {

    /** The order, locked until the transaction ends; empty when there is no such order. */
    Optional<ConfirmableOrder> lock(OrderId order);

    /** @param couponId the coupon the order applied, if any */
    record ConfirmableOrder(OrderId id, long number, OrderStatus status, Optional<UUID> couponId) {

        public ConfirmableOrder {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(couponId, "couponId");
        }
    }
}
