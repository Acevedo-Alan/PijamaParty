package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-14: creates payments, their checkout attempts and records the provider's checkouts (T-E3b-02). Each call joins
 * the caller's transaction, or runs in its own.
 */
public interface SavePayments {

    /** Stores the payment {@code PENDING}, unless the order already has one with that provider (then nothing). */
    void insertPending(NewPayment payment);

    /**
     * Records the payment's next checkout attempt, before the provider is called (RNF-20.8). Attempts of one payment
     * are numbered one after another, also when two start at once.
     *
     * @return empty when the order has no payment with that provider
     */
    Optional<CheckoutAttempt> startAttempt(UUID orderId, PaymentProvider provider, Instant at);

    /**
     * Records the provider's checkout in the attempt, only while the attempt has none and no attempt of the payment
     * holds that checkout already. It becomes the payment's current checkout only while no later attempt has one.
     *
     * @return whether it is now the payment's current checkout
     */
    boolean recordCheckout(UUID orderId, PaymentProvider provider, int attempt, Checkout checkout, Instant at);

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
