package com.pijamaparty.store.payments.api;

import java.util.Objects;

/** UC-14 (T-E3-29): the answer of {@link CardPayments#pay}, what the buyer is told at once. */
public sealed interface CardPaymentResult {

    /** Approved: the order is paid. */
    record Approved() implements CardPaymentResult {}

    /** Pending or in review at the provider: the webhook brings the result later (UC-15). */
    record InProcess() implements CardPaymentResult {}

    /** Rejected; the buyer may try another card while the reservation lasts. */
    record Rejected(CardRejection reason) implements CardPaymentResult {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** The provider could not be reached: nothing was charged, and paying can be retried. */
    record ProviderUnavailable() implements CardPaymentResult {}
}
