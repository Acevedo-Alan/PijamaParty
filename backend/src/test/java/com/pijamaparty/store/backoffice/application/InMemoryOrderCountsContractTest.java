package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import java.time.Instant;
import java.util.UUID;

class InMemoryOrderCountsContractTest extends OrderCountsContractTest<InMemoryOrderCounts> {

    @Override
    protected InMemoryOrderCounts createSubject() {
        return new InMemoryOrderCounts();
    }

    @Override
    protected UUID givenOrder(Status status, Instant createdAt) {
        return subject.order(status, createdAt);
    }

    @Override
    protected void givenReceivedWithdrawal() {
        subject.withdrawalReceived();
    }
}
