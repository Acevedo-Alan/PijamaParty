package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-28 (T-E4-16): an order of a customer as its data download holds it: everything the order keeps of the buyer,
 * the national ID included, because it is the customer's own data (right of access). Personal data:
 * {@link #toString()} never prints it (R-05).
 *
 * @param lines in the cart's order
 */
public record ExportedOrder(OrderId id, long number, OrderStatus status, Instant createdAt,
                            Optional<Instant> deliveredAt, Buyer buyer, ShippingAddress address, Province province,
                            List<OrderLine> lines, OrderAmounts amounts, Optional<String> couponCode,
                            String paymentProvider, Optional<String> shipmentTrackingNumber) {

    public ExportedOrder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(province, "province");
        lines = List.copyOf(lines);
        Objects.requireNonNull(amounts, "amounts");
        Objects.requireNonNull(couponCode, "couponCode");
        Objects.requireNonNull(paymentProvider, "paymentProvider");
        Objects.requireNonNull(shipmentTrackingNumber, "shipmentTrackingNumber");
    }

    @Override
    public String toString() {
        return "ExportedOrder[id=" + id + ", number=" + number + ", status=" + status + ", ***]";
    }

    /** The order's copy of the buyer's data. */
    public record Buyer(String firstName, String lastName, String email, String phone, String nationalId) {

        @Override
        public String toString() {
            return "Buyer[***]";
        }
    }
}
