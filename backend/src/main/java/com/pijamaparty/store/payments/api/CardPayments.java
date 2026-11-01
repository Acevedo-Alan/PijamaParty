package com.pijamaparty.store.payments.api;

/**
 * UC-14 (T-E3-29): charges a card the buyer typed on the store's page, in MercadoPago's card form, which turned it into
 * a single-use token: the card data never reaches the store. The charge's payment is then confirmed as a notification
 * would be (UC-15, R-06), so an approval publishes {@link PaymentApproved} before this answers. Run as {@code SYSTEM}
 * and outside any transaction, so no database connection waits for the provider (RNF-21).
 */
public interface CardPayments {

    CardPaymentResult pay(CardPaymentRequest request);
}
