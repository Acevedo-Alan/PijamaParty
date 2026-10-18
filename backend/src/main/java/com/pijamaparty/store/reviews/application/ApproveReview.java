package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.reviews.domain.ReviewId;
import java.util.Objects;

/**
 * UC-25 (T-E5-08): the administrator approves a review in any status, so it shows on the product page and counts
 * toward the average ({@code POST /api/admin/reviews/{id}/approve}). Approving it again changes nothing.
 */
public interface ApproveReview {

    Result execute(Command command);

    record Command(Actor actor, ReviewId review) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(review, "review");
        }
    }

    sealed interface Result {

        record Approved(ModeratedReview review) implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
