package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;

/** Outbound write port: changes the status of an order (UC-15, UC-16), within the caller's transaction. */
public interface OrderTransitions {

    /**
     * Moves the order from {@code from} to {@code to} only while it is in {@code from} (RNF-20.5), and answers whether
     * it moved: false means another step got there first, or there is no such order. The administrator's changes,
     * which may carry the tracking number or the delivery date, go through {@link StatusChanges} (UC-18).
     *
     * @throws RuntimeException when the state diagram does not allow the transition (an
     *     {@code IllegalArgumentException}, possibly translated by the persistence layer)
     */
    boolean transition(OrderId order, OrderStatus from, OrderStatus to, Instant at);
}
