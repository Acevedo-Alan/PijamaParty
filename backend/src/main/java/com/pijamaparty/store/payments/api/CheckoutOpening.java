package com.pijamaparty.store.payments.api;

import java.net.URI;
import java.util.Objects;

/** UC-14: the answer of {@link PaymentCheckouts#open}. */
public sealed interface CheckoutOpening {

    /** The provider's checkout, where the buyer is redirected to pay. */
    record Opened(URI checkoutUrl) implements CheckoutOpening {

        public Opened {
            Objects.requireNonNull(checkoutUrl, "checkoutUrl");
        }
    }

    /**
     * T-E3-29: the buyer pays on the store's page (MercadoPago's card form, {@link CardPayments}): the order's payment
     * is created and there is no checkout to redirect to.
     */
    record OnSite() implements CheckoutOpening {}

    /** The provider could not be reached or refused the checkout; opening it again later may succeed. */
    record ProviderUnavailable() implements CheckoutOpening {}
}
