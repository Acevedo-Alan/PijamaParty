package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.domain.Review;
import java.util.List;

/** Outbound read port (UC-28): every review of a customer, in the caller's transaction. */
public interface CustomerReviews {

    /** Oldest first; only those the actor can see. */
    List<Review> byAuthor(UserId author);
}
