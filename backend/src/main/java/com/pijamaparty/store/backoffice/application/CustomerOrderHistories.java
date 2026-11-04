package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.CustomerOrders.OrderHistory;
import java.util.UUID;

/**
 * Outbound read port (UC-34): a customer's orders and purchase figures as {@code ordering.api.CustomerOrders} reads
 * them, in the caller's transaction.
 */
public interface CustomerOrderHistories {

    /** One page of 20 from page 0, newest first, with the figures of all of them. */
    OrderHistory historyOf(UUID customerId, int page);
}
