package com.pijamaparty.store.payments.domain;

/**
 * UC-14 (T-E3b-02): one checkout created at the provider for a payment. A MODO payment request lasts minutes, so a
 * payment may need several; each is recorded before the provider is called (RNF-20.8).
 *
 * @param number            1, 2, ... within the payment
 * @param externalReference {@code {order number}-{attempt}}, MODO's {@code external_intention_id}
 */
public record CheckoutAttempt(int number, String externalReference) {

    public CheckoutAttempt {
        if (number <= 0) {
            throw new IllegalArgumentException("attempts are numbered from 1");
        }
        if (externalReference == null || externalReference.isBlank()) {
            throw new IllegalArgumentException("an attempt needs its external reference");
        }
    }

    /** The attempt {@code number} of the payment of order {@code orderNumber}. */
    public static CheckoutAttempt of(long orderNumber, int number) {
        if (orderNumber <= 0) {
            throw new IllegalArgumentException("orderNumber must be positive");
        }
        return new CheckoutAttempt(number, orderNumber + "-" + number);
    }
}
