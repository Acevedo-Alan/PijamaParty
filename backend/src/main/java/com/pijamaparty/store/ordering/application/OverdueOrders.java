package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.time.Instant;
import java.util.Optional;

/** Outbound read port: the orders whose stock reservation expired (UC-16), within the caller's transaction. */
public interface OverdueOrders {

    /**
     * The order awaiting payment whose reservation expired longest ago, at or before {@code now}, locked until the
     * transaction ends; an order another transaction holds locked is skipped. Empty when there is none.
     */
    Optional<OrderId> lockNextDue(Instant now);
}
