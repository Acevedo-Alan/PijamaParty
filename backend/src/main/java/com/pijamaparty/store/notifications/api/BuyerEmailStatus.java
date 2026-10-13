package com.pijamaparty.store.notifications.api;

import java.util.Optional;
import java.util.UUID;

/**
 * UC-17, UC-18 (T-E3-10): whether the payment-confirmed email reached the buyer of an order, for the back-office
 * alert. Read in its own transaction, or the caller's, as the caller: the administrator.
 */
public interface BuyerEmailStatus {

    /** Empty while the email is pending, once it is sent, or when the order has none. */
    Optional<Problem> problemOf(UUID orderId);

    /** Why the buyer did not get the email; {@code BOUNCED} arrives with T-E3-22. */
    enum Problem {
        /** It was rejected, or its attempts ran out: it will not be sent. */
        FAILED
    }
}
