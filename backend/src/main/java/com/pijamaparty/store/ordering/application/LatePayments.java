package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.time.Instant;

/**
 * Outbound write port (UC-15, UC-18): marks an expired or cancelled order whose payment was approved anyway, so the
 * back office alerts that it must be refunded. Within the caller's transaction.
 */
public interface LatePayments {

    /** Marks the order paid after closing at {@code at}, unless it already is; repeating it changes nothing. */
    void paidAfterClosing(OrderId order, Instant at);
}
