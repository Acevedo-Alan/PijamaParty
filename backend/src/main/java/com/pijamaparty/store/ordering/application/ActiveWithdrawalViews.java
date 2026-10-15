package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.Optional;

/** Outbound read port (UC-29, T-E3-26): an order's active withdrawal request as the back office shows it. */
public interface ActiveWithdrawalViews {

    /** Empty when the order has no {@code RECEIVED} request. */
    Optional<ActiveWithdrawal> activeViewOf(OrderId order);
}
