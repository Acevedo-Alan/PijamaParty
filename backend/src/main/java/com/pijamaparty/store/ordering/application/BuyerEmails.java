package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.time.Instant;

/** Outbound write port (UC-17, T-E3-23): replaces the buyer's email of an order, within the caller's transaction. */
public interface BuyerEmails {

    /**
     * @param email already normalized ({@code BuyerEmail}); personal data, never logged (R-05)
     * @return false when there is no such order for the actor
     */
    boolean replace(OrderId order, String email, Instant at);
}
