package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import java.util.Objects;

/**
 * UC-14: one payment provider (strategy, ARCHITECTURE.md section 5): MercadoPago now, MODO in E3b. Every
 * implementation passes {@code PaymentGatewayContractTest}. Called outside any transaction.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /** Creates the provider's checkout for exactly {@link CheckoutRequest#amount()}. Never throws for the provider. */
    Outcome createCheckout(CheckoutRequest request);

    sealed interface Outcome {

        record Created(Checkout checkout) implements Outcome {

            public Created {
                Objects.requireNonNull(checkout, "checkout");
            }
        }

        /** Unreachable, timed out, refused or answered something unusable. */
        record Unavailable() implements Outcome {}
    }
}
