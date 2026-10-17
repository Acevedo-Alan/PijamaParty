package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;

/** Outbound read port (UC-22): the orders of a customer, within the caller's transaction. */
public interface CustomerOrderPages {

    /** Only the orders of {@code customer}, newest first (then the highest number). */
    List<CustomerOrderSummary> page(UserId customer, int offset, int limit);
}
