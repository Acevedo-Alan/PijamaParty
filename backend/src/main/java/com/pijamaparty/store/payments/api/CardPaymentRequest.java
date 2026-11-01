package com.pijamaparty.store.payments.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-14 (T-E3-29): what charging a card for an order needs. The amount is the order total the server calculated
 * (R-02), and the payer's email is the order's, never the browser's.
 *
 * @param idempotencyKey the buyer's {@code Idempotency-Key} of this submit (RNF-20): the same key never charges twice
 */
public record CardPaymentRequest(UUID orderId, long orderNumber, Money amount, String payerEmail, Card card,
                                 UUID idempotencyKey) {

    public CardPaymentRequest {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(payerEmail, "payerEmail");
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (orderNumber <= 0) {
            throw new IllegalArgumentException("orderNumber must be positive");
        }
        if (amount.isZero()) {
            throw new IllegalArgumentException("a payment needs an amount");
        }
    }

    /** Personal data (R-05): {@link #toString()} hides it, and nothing logs it. */
    @Override
    public String toString() {
        return "CardPaymentRequest[orderNumber=" + orderNumber + ", ***]";
    }

    /**
     * What MercadoPago's card form answered for the card.
     *
     * @param token                the single-use card token
     * @param paymentMethodId      the card's brand at MercadoPago ({@code visa}, {@code master}, {@code debvisa} ...)
     * @param issuerId             the issuing bank at MercadoPago, when the form gives one
     * @param installments         1 to {@value #MAX_INSTALLMENTS}, as the buyer chose
     * @param identificationType   the cardholder's ID type ({@code DNI}, {@code CUIL} ...)
     * @param identificationNumber the cardholder's ID number: personal data, never stored nor logged
     */
    public record Card(String token, String paymentMethodId, Optional<String> issuerId, int installments,
                       String identificationType, String identificationNumber) {

        public static final int MAX_INSTALLMENTS = 48;

        public Card {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(paymentMethodId, "paymentMethodId");
            Objects.requireNonNull(issuerId, "issuerId");
            Objects.requireNonNull(identificationType, "identificationType");
            Objects.requireNonNull(identificationNumber, "identificationNumber");
            if (installments < 1 || installments > MAX_INSTALLMENTS) {
                throw new IllegalArgumentException("installments go from 1 to " + MAX_INSTALLMENTS);
            }
        }

        @Override
        public String toString() {
            return "Card[***]";
        }
    }
}
