package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;

/** Outbound port: publishes the events of {@code ordering.api} in the caller's transaction. */
public interface OrderEvents {

    void orderPaid(OrderId order, long number);
}
