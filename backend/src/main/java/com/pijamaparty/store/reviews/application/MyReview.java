package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What the customer sees of its own review of a product (UC-24), with its moderation status. Only sent to its author.
 *
 * @param updatedAt the last submission
 */
public record MyReview(int rating, Optional<String> text, ReviewStatus status, Instant updatedAt) {

    public MyReview {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    static MyReview of(Review review) {
        return new MyReview(review.content().rating(), review.content().text(), review.status(), review.updatedAt());
    }

    @Override
    public String toString() {
        return "MyReview[rating=" + rating + ", status=" + status + ", text=***]";
    }
}
