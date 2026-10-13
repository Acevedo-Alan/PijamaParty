package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** UC-14: creates payments and records their checkout. Each call joins the caller's transaction, or runs in its own. */
public interface SavePayments {

    /** Stores the payment {@code PENDING}, unless the order already has one with that provider (then nothing). */
    void insertPending(NewPayment payment);

    /**
     * Records the checkout of the order's payment only while it has none.
     *
     * @return false when the payment does not exist or already had a checkout (which stays)
     */
    boolean recordCheckout(UUID orderId, PaymentProvider provider, Checkout checkout, Instant at);

    record NewPayment(UUID id, UUID orderId, long orderNumber, PaymentProvider provider, Money amount,
                      Instant createdAt) {

        public NewPayment {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(createdAt, "createdAt");
            if (orderNumber <= 0) {
                throw new IllegalArgumentException("orderNumber must be positive");
            }
        }
    }
}
