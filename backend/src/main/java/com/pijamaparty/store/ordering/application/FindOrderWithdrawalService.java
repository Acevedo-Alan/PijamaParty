package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.OrderWithdrawals;
import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-29 (T-E3-26): {@link OrderWithdrawals} for the back-office order detail, read as the caller (the administrator). */
@Service
class FindOrderWithdrawalService implements OrderWithdrawals {

    private final ActiveWithdrawalViews views;

    FindOrderWithdrawalService(ActiveWithdrawalViews views) {
        this.views = views;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveWithdrawal> activeOf(UUID orderId) {
        return views.activeViewOf(OrderId.of(orderId));
    }
}
