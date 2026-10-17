package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound read port (UC-22): one order of a customer, for its detail in "Mis pedidos". Joins the caller's
 * transaction, or runs in its own.
 */
public interface CustomerOrderViews {

    /** Empty when there is no such order, or it is not one of {@code customer}'s. */
    Optional<CustomerOrder> find(UserId customer, OrderId order);

    /**
     * What the customer sees of its own order: the full address (the order's own copy, R-04), never the national ID.
     * {@link #toString()} hides the address (R-05).
     *
     * @param lines in the cart's order
     */
    record CustomerOrder(OrderId id, long number, OrderStatus status, Instant createdAt, List<OrderLine> lines,
                         OrderAmounts amounts, Optional<String> couponCode, ShippingAddress address,
                         Province province, Optional<String> shipmentTrackingNumber) {

        public CustomerOrder {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            lines = List.copyOf(lines);
            Objects.requireNonNull(amounts, "amounts");
            Objects.requireNonNull(couponCode, "couponCode");
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(province, "province");
            Objects.requireNonNull(shipmentTrackingNumber, "shipmentTrackingNumber");
        }

        @Override
        public String toString() {
            return "CustomerOrder[id=" + id + ", number=" + number + ", status=" + status + ", lines=" + lines.size()
                + ", amounts=" + amounts + ", address=***]";
        }
    }
}
