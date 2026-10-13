package com.pijamaparty.store.payments.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/**
 * UC-15: a payment as its provider reports it when asked (R-06), never as a notification claims it.
 *
 * @param externalId  the provider's id of the payment, 1 to {@value #MAX_EXTERNAL_ID_LENGTH} characters (V15)
 * @param orderNumber the order the payment is for (MercadoPago's {@code external_reference})
 * @param amount      what the buyer paid
 */
public record VerifiedPayment(String externalId, long orderNumber, VerifiedStatus status, Money amount) {

    public static final int MAX_EXTERNAL_ID_LENGTH = 200;

    public VerifiedPayment {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(amount, "amount");
        if (externalId.isBlank() || externalId.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw new IllegalArgumentException("a provider's payment id has 1 to " + MAX_EXTERNAL_ID_LENGTH
                + " characters");
        }
        if (orderNumber <= 0) {
            throw new IllegalArgumentException("orderNumber must be positive");
        }
    }
}
