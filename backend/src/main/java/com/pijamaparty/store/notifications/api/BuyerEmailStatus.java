package com.pijamaparty.store.notifications.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * UC-17, UC-18 (T-E3-10, T-E3-22): whether the payment-confirmed email reached the buyer of an order, for the
 * back-office alert. Read in its own transaction, or the caller's, as the caller: the administrator.
 */
public interface BuyerEmailStatus {

    /** Empty while the email is pending, once it is sent, or when the order has none. */
    Optional<Problem> problemOf(UUID orderId);

    /** UC-33: the orders whose buyer has a {@link Problem} with the payment-confirmed email. */
    Set<UUID> ordersWithProblems();

    /** Why the buyer did not get the email. */
    enum Problem {
        /** It was rejected, or its attempts ran out: it will not be sent. */
        FAILED,
        /** It was sent, and the provider reports it never reached the buyer. */
        BOUNCED
    }
}
