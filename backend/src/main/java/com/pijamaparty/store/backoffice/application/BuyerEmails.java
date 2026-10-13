package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.OrderAlert.BuyerEmailProblem;
import java.util.Optional;
import java.util.UUID;

/** Outbound read port (UC-18): whether the buyer of an order got its payment-confirmed email. */
public interface BuyerEmails {

    /** Empty while it is pending, once it is sent, or when the order has none. */
    Optional<BuyerEmailProblem> problemOf(UUID orderId);
}
