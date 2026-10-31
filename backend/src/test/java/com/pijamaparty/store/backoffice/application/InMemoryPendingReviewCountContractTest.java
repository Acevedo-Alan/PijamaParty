package com.pijamaparty.store.backoffice.application;

class InMemoryPendingReviewCountContractTest extends PendingReviewCountContractTest<InMemoryPendingReviewCount> {

    @Override
    protected InMemoryPendingReviewCount createSubject() {
        return new InMemoryPendingReviewCount();
    }

    @Override
    protected void givenReview(String status) {
        if (status.equals("PENDING")) {
            subject.submitted();
        }
    }
}
