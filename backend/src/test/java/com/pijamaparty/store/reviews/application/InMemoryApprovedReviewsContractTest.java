package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.time.Instant;

class InMemoryApprovedReviewsContractTest extends ApprovedReviewsContractTest<InMemoryReviewStore> {

    @Override
    protected InMemoryReviewStore createSubject() {
        return new InMemoryReviewStore();
    }

    @Override
    protected void moderated(ReviewId review, ReviewStatus status, Instant at) {
        subject.moderate(review, status, at);
    }
}
