package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import java.util.Objects;

/**
 * UC-15: asks one provider for a payment (strategy, ARCHITECTURE.md section 5; R-06). Apart from
 * {@link PaymentGateway}, since confirming a payment never creates a checkout (Interface Segregation). Every
 * implementation passes {@code PaymentVerifierContractTest}. Called outside any transaction.
 */
public interface PaymentVerifier {

    PaymentProvider provider();

    /** The payment as the provider reports it now. Never throws for the provider. */
    Verification verify(String externalPaymentId);

    sealed interface Verification {

        record Found(VerifiedPayment payment) implements Verification {

            public Found {
                Objects.requireNonNull(payment, "payment");
            }
        }

        /** The provider has no such payment. */
        record NotFound() implements Verification {}

        /** Unreachable, timed out, refused or answered something unusable. */
        record Unavailable() implements Verification {}
    }
}
