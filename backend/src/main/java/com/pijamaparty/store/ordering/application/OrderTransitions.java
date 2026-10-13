package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;

/** Outbound write port: changes the status of an order (UC-16, UC-18), within the caller's transaction. */
public interface OrderTransitions {

    /**
     * Moves the order from {@code from} to {@code to} only while it is in {@code from} (RNF-20.5), and answers whether
     * it moved: false means another step got there first, or there is no such order.
     *
     * A status that needs more data, {@code SHIPPED} (the tracking number) or {@code DELIVERED} (the delivery date),
     * arrives with its own operation (T-E3-10).
     *
     * @throws RuntimeException when the state diagram does not allow the transition (an
     *     {@code IllegalArgumentException}, possibly translated by the persistence layer)
     */
    boolean transition(OrderId order, OrderStatus from, OrderStatus to, Instant at);
}
