package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.util.List;
import java.util.Objects;

/**
 * UC-25 (T-E5-08): the back office lists the reviews in one status ({@code GET /api/admin/reviews}): pending ones from
 * the oldest submission to the newest, the others newest first.
 */
public interface ListReviewsForModeration {

    int PAGE_SIZE = 50;

    Result execute(Query query);

    /** @param page from 0 */
    record Query(Actor actor, ReviewStatus status, int page) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(status, "status");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }
    }

    sealed interface Result {

        /** @param reviews the page asked for; empty past the last one */
        record Listed(List<ModeratedReview> reviews, boolean hasMore) implements Result {

            public Listed {
                reviews = List.copyOf(reviews);
            }
        }

        record Forbidden() implements Result {}
    }
}
