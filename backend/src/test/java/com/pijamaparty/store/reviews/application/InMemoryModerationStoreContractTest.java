package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.Review;

class InMemoryModerationStoreContractTest extends ModerationStoreContractTest<InMemoryReviewStore> {

    @Override
    protected InMemoryReviewStore createSubject() {
        return new InMemoryReviewStore();
    }

    @Override
    protected void stored(Review review) {
        subject.save(review);
    }
}
