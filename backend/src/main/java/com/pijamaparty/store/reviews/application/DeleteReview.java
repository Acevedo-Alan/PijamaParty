package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.reviews.domain.ReviewId;
import java.util.Objects;

/**
 * UC-25 (T-E5-08): the administrator deletes a review ({@code DELETE /api/admin/reviews/{id}}), so its author can
 * write a new one.
 */
public interface DeleteReview {

    Result execute(Command command);

    record Command(Actor actor, ReviewId review) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(review, "review");
        }
    }

    sealed interface Result {

        record Deleted() implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
