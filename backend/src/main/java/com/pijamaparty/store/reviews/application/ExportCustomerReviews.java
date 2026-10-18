package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-28 (T-E5-06): the reviews section of a customer's data download. Joins the download's transaction, which runs as
 * that customer, so Row Level Security shows only its own reviews (V37).
 */
public interface ExportCustomerReviews {

    /** Oldest first. */
    List<ExportedReview> execute(Query query);

    record Query(UserId customer) {

        public Query {
            Objects.requireNonNull(customer, "customer");
        }
    }

    /**
     * A review as the download tells it. The customer's own data: {@link #toString()} never prints its text.
     *
     * @param productName empty when the catalog no longer has the product
     * @param updatedAt the last submission
     */
    record ExportedReview(Optional<String> productName, int rating, Optional<String> text, ReviewStatus status,
                          Instant createdAt, Instant updatedAt) {

        public ExportedReview {
            Objects.requireNonNull(productName, "productName");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }

        @Override
        public String toString() {
            return "ExportedReview[rating=" + rating + ", status=" + status + ", ***]";
        }
    }
}
