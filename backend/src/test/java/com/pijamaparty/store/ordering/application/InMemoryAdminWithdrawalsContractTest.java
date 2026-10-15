package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawableOrders.WithdrawableOrder;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.util.Optional;

class InMemoryAdminWithdrawalsContractTest extends AdminWithdrawalsContractTest<InMemoryWithdrawalStore> {

    @Override
    protected InMemoryWithdrawalStore createSubject() {
        return new InMemoryWithdrawalStore();
    }

    @Override
    protected WithdrawableOrder placed(OrderStatus status) {
        return subject.place(status, PLACED_AT, Optional.empty());
    }

    @Override
    protected void requested(NewWithdrawal withdrawal) {
        subject.insert(withdrawal);
    }
}
