package com.pijamaparty.store.backoffice.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Outbound read port (UC-34): the orders linked to each customer of a page, in the caller's transaction. */
public interface CustomerOrderTotals {

    /** One entry per id of {@code customerIds}, 0 for a customer without orders. */
    Map<UUID, Long> countsOf(Collection<UUID> customerIds);
}
