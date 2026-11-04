package com.pijamaparty.store.ordering.application;

import java.util.List;

/** Outbound read port (UC-18): the back-office list of orders, within the caller's transaction. */
public interface OrderPages {

    /**
     * Newest first (then the highest number), only those {@code filter} lets through: in its status, matching its
     * search (that number, or the buyer's first name, last name, full name or email containing the text, ignoring case
     * and accents, every character literal) and created in its period.
     */
    List<OrderSummary> page(OrderFilter filter, int offset, int limit);
}
