package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.Optional;

/** Outbound read port (UC-18): an order as the back office shows it, within the caller's transaction. */
public interface AdminOrderViews {

    /**
     * The order with its lines, with no {@linkplain AdminOrder#nextStatuses() next statuses}: those are the use
     * case's. Empty when there is no such order.
     */
    Optional<AdminOrder> adminViewOf(OrderId order);
}
