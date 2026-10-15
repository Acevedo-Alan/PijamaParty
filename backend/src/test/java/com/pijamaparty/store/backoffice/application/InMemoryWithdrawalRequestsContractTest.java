package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import java.util.UUID;

class InMemoryWithdrawalRequestsContractTest extends WithdrawalRequestsContractTest<InMemoryWithdrawalRequests> {

    @Override
    protected InMemoryWithdrawalRequests createSubject() {
        return new InMemoryWithdrawalRequests();
    }

    @Override
    protected UUID givenRequested(String code, boolean outOfTerm) {
        UUID order = UUID.randomUUID();
        subject.requested(order, new ActiveWithdrawal(UUID.randomUUID(), code, outOfTerm, REQUESTED_AT));
        return order;
    }

    @Override
    protected UUID givenResolved(String code) {
        return UUID.randomUUID();
    }

    @Override
    protected UUID givenNoRequest() {
        return UUID.randomUUID();
    }
}
