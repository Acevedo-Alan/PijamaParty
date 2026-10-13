package com.pijamaparty.store.payments.api;

/**
 * UC-14: the payment of an order at its provider. Run as {@code SYSTEM} and outside any transaction: each step runs
 * in its own short transaction, so no database connection waits for the provider (RNF-21).
 */
public interface PaymentCheckouts {

    /**
     * Creates the order's payment and the provider's checkout, once per order and provider: when the checkout already
     * exists (a retry, T-E3-08), its URL is answered without calling the provider.
     */
    CheckoutOpening open(CheckoutRequest request);
}
