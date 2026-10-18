package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.Review;
import java.util.Optional;

/** Outbound read port (UC-24): a customer's review of a product, in the caller's transaction. */
public interface LoadReview {

    /** Empty when {@code author} has not reviewed {@code product}, or the actor cannot see its review. */
    Optional<Review> findByAuthorAndProduct(UserId author, ProductId product);
}
