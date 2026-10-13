package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** UC-14: what paying an order needs, read as its buyer. Joins the caller's transaction, or runs in its own. */
public interface OrdersToPay {

    /** Empty when there is no such order, or Row Level Security hides it from the actor. */
    Optional<OrderToPay> find(OrderId order);

    /** The buyer's name and email prefill the provider's checkout; {@link #toString()} hides them (R-05). */
    record OrderToPay(OrderId id, long number, OrderStatus status, Money total, PaymentProvider provider,
                      String buyerName, String buyerEmail, Instant reservationExpiresAt) {

        public OrderToPay {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(total, "total");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(buyerName, "buyerName");
            Objects.requireNonNull(buyerEmail, "buyerEmail");
            Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
        }

        @Override
        public String toString() {
            return "OrderToPay[id=" + id + ", number=" + number + ", status=" + status + ", total=" + total
                + ", provider=" + provider + ", buyer=***, reservationExpiresAt=" + reservationExpiresAt + "]";
        }
    }
}
