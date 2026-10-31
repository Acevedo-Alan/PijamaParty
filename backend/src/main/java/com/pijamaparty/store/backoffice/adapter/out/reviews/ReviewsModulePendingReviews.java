package com.pijamaparty.store.backoffice.adapter.out.reviews;

import com.pijamaparty.store.backoffice.application.PendingReviewCount;
import com.pijamaparty.store.reviews.api.PendingReviews;
import org.springframework.stereotype.Component;

/** Reads the pending reviews through the public API of the reviews module, in the caller's transaction. */
@Component
class ReviewsModulePendingReviews implements PendingReviewCount {

    private final PendingReviews pendingReviews;

    ReviewsModulePendingReviews(PendingReviews pendingReviews) {
        this.pendingReviews = pendingReviews;
    }

    @Override
    public long pending() {
        return pendingReviews.count();
    }
}
