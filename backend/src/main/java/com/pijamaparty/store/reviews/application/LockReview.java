package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewId;
import java.util.Optional;

/** Outbound read port (UC-25): one review for the administrator's decision, in the caller's transaction. */
public interface LockReview {

    /**
     * The review, locked until the transaction ends, so an edit of its author waits for the decision and is never
     * overwritten by it. Empty when there is none, or the actor cannot see it.
     */
    Optional<Review> lockById(ReviewId id);
}
