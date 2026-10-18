package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Outbound read port (UC-24): a customer's orders by the variants they hold, in the caller's transaction. */
public interface PurchasedVariants {

    /** Whether {@code customer} has an order in one of {@code statuses} with any of {@code variantIds}. */
    boolean hasOrderWithAny(UserId customer, Set<OrderStatus> statuses, Collection<UUID> variantIds);
}
