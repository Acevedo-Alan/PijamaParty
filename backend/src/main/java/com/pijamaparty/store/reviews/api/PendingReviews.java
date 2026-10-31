package com.pijamaparty.store.reviews.api;

/**
 * UC-33: how many reviews wait for moderation (UC-25), for the summary's "Para hacer hoy". Read in its own
 * transaction, or the caller's, as the caller: the administrator. A count only, no personal data.
 */
public interface PendingReviews {

    long count();
}
