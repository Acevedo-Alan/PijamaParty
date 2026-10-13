package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.util.Optional;

/** Outbound read port (UC-18): the status of an order, within the caller's transaction, which must exist. */
public interface OrderStatuses {

    /** The order's status, locked until the transaction ends; empty when there is no such order. */
    Optional<OrderStatus> lockStatus(OrderId order);
}
