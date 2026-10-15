package com.pijamaparty.store.payments.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/**
 * UC-15: a payment as its provider reports it when asked (R-06), never as a notification claims it.
 *
 * @param externalId the provider's id of the payment, 1 to {@value #MAX_EXTERNAL_ID_LENGTH} characters (V15)
 * @param reference  the store's payment it is for: the order number (MercadoPago's {@code external_reference}) or the
 *                   checkout it paid (MODO, T-E3b-03)
 * @param amount     what the buyer paid
 */
public record VerifiedPayment(String externalId, PaymentReference reference, VerifiedStatus status, Money amount) {

    public static final int MAX_EXTERNAL_ID_LENGTH = 200;

    public VerifiedPayment {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(amount, "amount");
        if (externalId.isBlank() || externalId.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw new IllegalArgumentException("a provider's payment id has 1 to " + MAX_EXTERNAL_ID_LENGTH
                + " characters");
        }
    }

    /** A payment that carries its order number. */
    public VerifiedPayment(String externalId, long orderNumber, VerifiedStatus status, Money amount) {
        this(externalId, new PaymentReference.ByOrderNumber(orderNumber), status, amount);
    }
}
