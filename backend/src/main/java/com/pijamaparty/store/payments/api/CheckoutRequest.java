package com.pijamaparty.store.payments.api;

import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-14: what the provider's checkout of an order needs. The amount is the order total the server calculated (R-02).
 *
 * @param expiresAt the end of the order's stock reservation (UC-16): the checkout cannot be paid after it
 */
public record CheckoutRequest(UUID orderId, long orderNumber, Money amount, PaymentProvider provider, Payer payer,
                              Instant expiresAt) {

    public CheckoutRequest {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (orderNumber <= 0) {
            throw new IllegalArgumentException("orderNumber must be positive");
        }
        if (amount.isZero()) {
            throw new IllegalArgumentException("a payment needs an amount");
        }
    }

    /** Prefills the provider's checkout; personal data, so {@link #toString()} hides it (R-05). */
    public record Payer(String name, String email) {

        public Payer {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(email, "email");
        }

        @Override
        public String toString() {
            return "Payer[***]";
        }
    }
}
