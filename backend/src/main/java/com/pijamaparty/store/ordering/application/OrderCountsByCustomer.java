package com.pijamaparty.store.ordering.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Outbound read port (UC-34): the orders linked to each customer, within the caller's transaction. */
public interface OrderCountsByCustomer {

    /** Only the customers with orders; at most one entry per id. */
    Map<UUID, Long> countsOf(Collection<UUID> customerIds);
}
