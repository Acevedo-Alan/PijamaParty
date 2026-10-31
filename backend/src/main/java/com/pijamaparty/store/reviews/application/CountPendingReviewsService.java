package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.api.PendingReviews;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-33: {@link PendingReviews} for the back-office summary, read as the caller (the administrator). */
@Service
class CountPendingReviewsService implements PendingReviews {

    private final ReviewsForModeration reviews;

    CountPendingReviewsService(ReviewsForModeration reviews) {
        this.reviews = reviews;
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return reviews.count(ReviewStatus.PENDING);
    }
}
