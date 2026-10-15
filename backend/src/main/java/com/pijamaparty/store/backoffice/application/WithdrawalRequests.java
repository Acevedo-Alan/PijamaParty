package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import java.util.Optional;
import java.util.UUID;

/** Outbound read port (UC-29, T-E3-26): whether an order is being withdrawn, for its back-office detail. */
public interface WithdrawalRequests {

    /** Empty when the order has no received withdrawal request. */
    Optional<ActiveWithdrawal> activeOf(UUID orderId);
}
