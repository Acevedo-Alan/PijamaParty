package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.WithdrawableOrders.WithdrawableOrder;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

class InMemoryWithdrawalStoreContractTest extends WithdrawalStoreContractTest<InMemoryWithdrawalStore> {

    @Override
    protected InMemoryWithdrawalStore createSubject() {
        return new InMemoryWithdrawalStore();
    }

    @Override
    protected WithdrawableOrder placed(OrderStatus status, Instant createdAt, Optional<Instant> deliveredAt) {
        return subject.place(status, createdAt, deliveredAt);
    }

    @Override
    protected void resolved(UUID request, Instant at) {
        subject.resolve(request, at);
    }
}
