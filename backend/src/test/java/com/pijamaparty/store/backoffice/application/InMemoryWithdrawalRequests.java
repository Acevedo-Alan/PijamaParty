package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fake of {@link WithdrawalRequests}: the orders with a received withdrawal request. */
public class InMemoryWithdrawalRequests implements WithdrawalRequests {

    private final Map<UUID, ActiveWithdrawal> active = new HashMap<>();

    /** Test arrangement: the buyer asked to withdraw from the order. */
    public synchronized void requested(UUID orderId, ActiveWithdrawal withdrawal) {
        active.put(orderId, withdrawal);
    }

    @Override
    public synchronized Optional<ActiveWithdrawal> activeOf(UUID orderId) {
        return Optional.ofNullable(active.get(orderId));
    }
}
