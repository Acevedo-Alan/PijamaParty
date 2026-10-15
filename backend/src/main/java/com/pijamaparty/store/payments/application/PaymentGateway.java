package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import java.util.Objects;

/**
 * UC-14: one payment provider (strategy, ARCHITECTURE.md section 5): MercadoPago and MODO (T-E3b-02). Every
 * implementation passes {@code PaymentGatewayContractTest}. Called outside any transaction.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /**
     * Whether buyers may choose the provider at the checkout (T-E3b-02: MODO is switched on by configuration). An
     * order already placed with it still pays through it.
     */
    default boolean enabled() {
        return true;
    }

    /**
     * Creates the provider's checkout of the attempt for exactly {@link CheckoutRequest#amount()}. Never throws for
     * the provider.
     */
    Outcome createCheckout(CheckoutRequest request, CheckoutAttempt attempt);

    sealed interface Outcome {

        /** The checkout, with its expiry when the provider sets one shorter than the reservation (MODO). */
        record Created(Checkout checkout) implements Outcome {

            public Created {
                Objects.requireNonNull(checkout, "checkout");
            }
        }

        /** Unreachable, timed out, refused or answered something unusable. */
        record Unavailable() implements Outcome {}
    }
}
