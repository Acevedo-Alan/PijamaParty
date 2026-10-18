package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.Review;

/** Outbound write port (UC-25): stores the administrator's decision on a locked review, in the caller's transaction. */
public interface SaveModeration {

    /** Stores only the status and the moderation time of the review; the rest stays as its author left it. */
    void saveDecision(Review review);
}
