package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ReviewId;

/** Outbound write port (UC-25): deletes one review, in the caller's transaction. */
public interface RemoveReview {

    /** False when there is no such review, or the actor cannot delete it. */
    boolean remove(ReviewId id);
}
