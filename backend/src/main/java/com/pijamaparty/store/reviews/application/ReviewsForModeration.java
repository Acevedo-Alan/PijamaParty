package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.util.List;

/** Outbound read port (UC-25): the reviews the administrator moderates, in the caller's transaction. */
public interface ReviewsForModeration {

    /**
     * Up to {@code limit} reviews in {@code status}, skipping the first {@code offset}: pending ones from the oldest
     * submission to the newest, the others newest first (last submission, then id).
     */
    List<Review> withStatus(ReviewStatus status, int offset, int limit);
}
