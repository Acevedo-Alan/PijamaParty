package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.CardPaymentRequest;
import com.pijamaparty.store.payments.api.CardRejection;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-14 (T-E3-29): a provider whose buyers pay on the store's page, with its card form: MercadoPago. Apart from
 * {@link PaymentGateway}, whose providers redirect the buyer to a checkout (Interface Segregation). Every implementation
 * passes {@code CardChargesContractTest}. Called outside any transaction.
 */
public interface CardCharges {

    PaymentProvider provider();

    /** Whether buyers may choose the provider at the checkout. */
    default boolean enabled() {
        return true;
    }

    /** The key the browser's card form turns cards into tokens with; empty when it is not configured. */
    Optional<String> publicKey();

    /** Charges exactly {@link CardCharge#amount()}; the same idempotency key never charges twice. Never throws. */
    Charge charge(CardCharge charge);

    /** @param idempotencyKey the buyer's key of this submit; the adapter derives the provider's key from it */
    record CardCharge(long orderNumber, Money amount, String payerEmail, CardPaymentRequest.Card card,
                      UUID idempotencyKey) {

        public CardCharge {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(payerEmail, "payerEmail");
            Objects.requireNonNull(card, "card");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        }

        @Override
        public String toString() {
            return "CardCharge[orderNumber=" + orderNumber + ", ***]";
        }
    }

    sealed interface Charge {

        /**
         * The provider created the payment.
         *
         * @param rejection why, when the status is {@code REJECTED}
         */
        record Charged(String paymentId, ChargeStatus status, Optional<CardRejection> rejection) implements Charge {

            public Charged {
                Objects.requireNonNull(paymentId, "paymentId");
                Objects.requireNonNull(status, "status");
                Objects.requireNonNull(rejection, "rejection");
            }
        }

        /** The provider refused the request itself, as for a card token that expired or was used. */
        record Refused() implements Charge {}

        /** Unreachable, timed out, failed or answered something unusable. */
        record Unavailable() implements Charge {}
    }

    /** The created payment's status, as the provider answered the charge. */
    enum ChargeStatus {
        APPROVED,
        IN_PROCESS,
        REJECTED
    }
}
