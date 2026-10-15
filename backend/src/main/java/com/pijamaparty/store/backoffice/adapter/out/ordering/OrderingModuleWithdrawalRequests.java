package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.backoffice.application.WithdrawalRequests;
import com.pijamaparty.store.ordering.api.OrderWithdrawals;
import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads an order's active withdrawal request through the public API of the ordering module, in the caller's transaction. */
@Component
class OrderingModuleWithdrawalRequests implements WithdrawalRequests {

    private final OrderWithdrawals withdrawals;

    OrderingModuleWithdrawalRequests(OrderWithdrawals withdrawals) {
        this.withdrawals = withdrawals;
    }

    @Override
    public Optional<ActiveWithdrawal> activeOf(UUID orderId) {
        return withdrawals.activeOf(orderId);
    }
}
