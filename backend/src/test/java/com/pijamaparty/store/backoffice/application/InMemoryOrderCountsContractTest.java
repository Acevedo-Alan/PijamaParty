package com.pijamaparty.store.backoffice.application;

class InMemoryOrderCountsContractTest extends OrderCountsContractTest<InMemoryOrderCounts> {

    @Override
    protected InMemoryOrderCounts createSubject() {
        return new InMemoryOrderCounts();
    }

    @Override
    protected void givenPaid() {
        subject.paid();
    }

    @Override
    protected void givenAwaitingPayment() {
        // The fake counts only the paid orders.
    }

    @Override
    protected void givenPreparing() {
        // The fake counts only the paid orders.
    }
}
