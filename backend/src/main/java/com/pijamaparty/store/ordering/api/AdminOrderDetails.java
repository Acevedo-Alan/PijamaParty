package com.pijamaparty.store.ordering.api;

import com.pijamaparty.store.ordering.api.PaidOrders.Address;
import com.pijamaparty.store.ordering.api.PaidOrders.Line;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-18 (T-E3-10): an order as the back office shows it. Read in its own transaction, or the caller's, as the caller:
 * the administrator, whom Row Level Security lets read every order.
 */
public interface AdminOrderDetails {

    /** Empty when there is no such order, or Row Level Security hides it from the actor. */
    Optional<AdminOrder> find(UUID orderId);

    /** The seven states of an order (GLOSSARY.md). */
    enum Status {
        AWAITING_PAYMENT,
        PAID,
        PREPARING,
        SHIPPED,
        DELIVERED,
        CANCELLED,
        EXPIRED
    }

    /**
     * The order as the buyer placed it (R-04) and where it stands. Personal data (R-05): {@link #toString()} prints
     * only the number.
     *
     * @param nextStatuses what the administrator may move it to, in the order of {@link Status}
     * @param lines in the cart's order
     * @param paymentProvider the constant of {@code payments.api.PaymentProvider}, e.g. {@code MERCADOPAGO}
     * @param paidAfterClosing its payment was approved after it expired or was cancelled: to be refunded
     */
    record AdminOrder(UUID orderId, long number, Status status, List<Status> nextStatuses, Instant createdAt,
                      Instant updatedAt, Optional<Instant> deliveredAt, Buyer buyer, Address address,
                      List<Line> lines, Money subtotal, Money discount, Optional<String> couponCode, Money shipping,
                      Money total, String paymentProvider, Optional<String> shipmentTrackingNumber,
                      boolean paidAfterClosing) {

        public AdminOrder {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(status, "status");
            nextStatuses = List.copyOf(nextStatuses);
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            Objects.requireNonNull(deliveredAt, "deliveredAt");
            Objects.requireNonNull(buyer, "buyer");
            Objects.requireNonNull(address, "address");
            lines = List.copyOf(lines);
            Objects.requireNonNull(subtotal, "subtotal");
            Objects.requireNonNull(discount, "discount");
            Objects.requireNonNull(couponCode, "couponCode");
            Objects.requireNonNull(shipping, "shipping");
            Objects.requireNonNull(total, "total");
            Objects.requireNonNull(paymentProvider, "paymentProvider");
            Objects.requireNonNull(shipmentTrackingNumber, "shipmentTrackingNumber");
        }

        /** The same order with these next statuses. */
        public AdminOrder withNextStatuses(List<Status> next) {
            return new AdminOrder(orderId, number, status, next, createdAt, updatedAt, deliveredAt, buyer, address,
                lines, subtotal, discount, couponCode, shipping, total, paymentProvider, shipmentTrackingNumber,
                paidAfterClosing);
        }

        @Override
        public String toString() {
            return "AdminOrder[number=" + number + ", status=" + status + ", ***]";
        }
    }

    /** Who placed the order. Personal data (R-05): {@link #toString()} never prints it. */
    record Buyer(String name, String email, String phone, String nationalId) {

        public Buyer {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(phone, "phone");
            Objects.requireNonNull(nationalId, "nationalId");
        }

        @Override
        public String toString() {
            return "Buyer[***]";
        }
    }
}
