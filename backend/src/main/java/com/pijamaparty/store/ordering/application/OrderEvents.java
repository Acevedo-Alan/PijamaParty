package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;

/** Outbound port: publishes the events of {@code ordering.api} in the caller's transaction. */
public interface OrderEvents {

    void orderPaid(OrderId order, long number);

    /** UC-17 (T-E3-23): the buyer corrected the email of a paid order. */
    void orderEmailCorrected(OrderId order, long number);
}
