package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.time.Instant;

/**
 * Outbound write port (UC-17, T-E3-09; T-E3-14): one more tracking token of an order, for an email or a repeated
 * checkout, in the caller's transaction.
 */
public interface SaveTrackingLinks {

    /**
     * Keeps the hash of a new token of the order (RNF-29.6).
     *
     * @return false when there is no such order
     */
    boolean add(OrderId order, String tokenHash, Instant at);
}
