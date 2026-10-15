package com.pijamaparty.store.payments.domain;

import java.util.Objects;

/**
 * UC-15 (T-E3b-03): how a provider's payment leads to the store's payment it is for. MercadoPago reports the order
 * number ({@code external_reference}); MODO does not return its {@code external_intention_id} when asked, so its
 * payments are found by the checkout (the payment request) they paid, through {@code payment_checkout}.
 */
public sealed interface PaymentReference {

    /** The payment carries the order number. */
    record ByOrderNumber(long orderNumber) implements PaymentReference {

        public ByOrderNumber {
            if (orderNumber <= 0) {
                throw new IllegalArgumentException("orderNumber must be positive");
            }
        }
    }

    /** The payment carries the id of the provider's checkout it paid (V27's {@code checkout_id}). */
    record ByCheckout(String checkoutId) implements PaymentReference {

        public ByCheckout {
            Objects.requireNonNull(checkoutId, "checkoutId");
            if (checkoutId.isBlank() || checkoutId.length() > Checkout.MAX_ID_LENGTH) {
                throw new IllegalArgumentException("a checkout id has 1 to " + Checkout.MAX_ID_LENGTH + " characters");
            }
        }
    }
}
