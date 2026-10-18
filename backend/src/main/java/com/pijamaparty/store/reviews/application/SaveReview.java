package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.Review;

/**
 * Outbound write port (UC-24): stores a customer's review of a product, in the caller's transaction. There is one per
 * customer and product, so a review saved while another one of the same author and product is stored replaces that
 * one's rating, text, author name, status and times of submission and moderation, and keeps its id and creation time.
 */
public interface SaveReview {

    Outcome save(Review review);

    enum Outcome {
        /** The author had no review of the product. */
        CREATED,
        /** The author's review of the product was replaced. */
        REPLACED
    }
}
