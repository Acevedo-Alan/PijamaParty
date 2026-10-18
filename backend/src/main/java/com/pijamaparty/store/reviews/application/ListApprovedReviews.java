package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.RatingSummary;
import java.util.List;
import java.util.Objects;

/**
 * UC-24 (T-E5-07): the approved reviews of a listed product with their average and count, for anyone who opens its page
 * ({@code GET /api/catalog/products/{slug}/reviews}).
 */
public interface ListApprovedReviews {

    int PAGE_SIZE = 10;

    Result execute(Query query);

    /**
     * @param slug as the visitor sent it
     * @param page from 0
     */
    record Query(String slug, int page) {

        public Query {
            Objects.requireNonNull(slug, "slug");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }
    }

    sealed interface Result {

        /** @param reviews the page asked for, newest first; empty past the last one */
        record Listed(RatingSummary summary, List<PublishedReview> reviews) implements Result {

            public Listed {
                Objects.requireNonNull(summary, "summary");
                reviews = List.copyOf(reviews);
            }
        }

        /** No product has this slug, or visitors do not see it. */
        record ProductNotFound() implements Result {}
    }
}
