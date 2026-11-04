package com.pijamaparty.store.ordering.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * UC-34 (T-E8-10): how many orders each customer of the back office's list has, in any status. Only the orders linked
 * to the account count, never a guest's with the same email (UC-21, UC-28). Read as the caller (the administrator),
 * in its transaction.
 */
public interface CustomerOrderCounts {

    /** The number of orders of each of {@code customerIds}, one grouped read; a customer without orders has 0. */
    Map<UUID, Long> countsOf(Collection<UUID> customerIds);
}
