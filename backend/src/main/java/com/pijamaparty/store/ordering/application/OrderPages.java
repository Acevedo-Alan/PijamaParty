package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.util.List;
import java.util.Optional;

/** Outbound read port (UC-18): the back-office list of orders, within the caller's transaction. */
public interface OrderPages {

    /** Newest first (then the highest number), only those in {@code status} when it is present. */
    List<OrderSummary> page(Optional<OrderStatus> status, int offset, int limit);
}
