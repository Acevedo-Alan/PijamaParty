package com.pijamaparty.store.ordering.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * UC-14: an order can still be paid while it awaits payment and its reservation is not over. Shared by the payment
 * opened after the checkout (T-E3-06) and the retry from the tracking link (T-E3-08).
 */
public final class OrderPayability {

    private OrderPayability() {
    }

    /** Whether the order can be paid at {@code now}; the reservation is over from its expiry on. */
    public static boolean isPayable(OrderStatus status, Instant reservationExpiresAt, Instant now) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
        Objects.requireNonNull(now, "now");
        return status == OrderStatus.AWAITING_PAYMENT && reservationExpiresAt.isAfter(now);
    }
}
