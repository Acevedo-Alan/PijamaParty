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

    /** The provider could not be reached or refused the checkout; opening it again later may succeed. */
    record ProviderUnavailable() implements CheckoutOpening {}
}
