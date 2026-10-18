package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;

/** Outbound write port (UC-28): deletes every review of a customer, in the caller's transaction. */
public interface RemoveCustomerReviews {

    /** The number of reviews deleted; 0 when it had none. */
    int removeByAuthor(UserId author);
}
