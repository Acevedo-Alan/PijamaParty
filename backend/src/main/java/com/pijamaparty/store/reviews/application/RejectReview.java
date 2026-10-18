package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.reviews.domain.ReviewId;
import java.util.Objects;

/**
 * UC-25 (T-E5-08): the administrator rejects a review in any status, so it leaves the product page and the average
 * ({@code POST /api/admin/reviews/{id}/reject}). Rejecting it again changes nothing.
 */
public interface RejectReview {

    Result execute(Command command);

    record Command(Actor actor, ReviewId review) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(review, "review");
        }
    }

    sealed interface Result {

        record Rejected(ModeratedReview review) implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
