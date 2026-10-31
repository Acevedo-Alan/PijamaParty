package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderStatus;

/** Outbound read port (T-E8-05, UC-33): how many orders are in a status, within the caller's transaction. */
public interface OrderStatusCounts {

    long count(OrderStatus status);
}
