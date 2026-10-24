package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import java.util.Objects;
import java.util.Optional;

/** Outbound read port (UC-18): an order the administrator changes, within the caller's transaction, which must exist. */
public interface OrderStatuses {

    /** The order, locked until the transaction ends; empty when there is no such order. */
    Optional<LockedOrder> lockForChange(OrderId order);

    /**
     * What a change of the administrator decides on, and records in the activity log (UC-37); never the buyer's data.
     *
     * @param trackingNumber present once the order was shipped
     */
    record LockedOrder(long number, OrderStatus status, Optional<ShipmentTrackingNumber> trackingNumber) {

        public LockedOrder {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(trackingNumber, "trackingNumber");
        }

        /** How the store shows the number, {@code #1001}. */
        public String label() {
            return "#" + number;
        }
    }
}
