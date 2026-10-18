package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.ReviewForm;
import java.util.Objects;
import java.util.Set;

/**
 * UC-24 (T-E5-06): the signed-in customer reviews a product it bought, or edits its review of it
 * ({@code PUT /api/account/reviews/{productId}}). Either way the review waits for moderation (UC-25).
 */
public interface SubmitReview {

    Result execute(Command command);

    record Command(Actor actor, ProductId product, ReviewForm review) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(product, "product");
            Objects.requireNonNull(review, "review");
        }
    }

    sealed interface Result {

        /** The customer's first review of the product. */
        record Created(MyReview review) implements Result {}

        /** The customer's review of the product was replaced, and went back to moderation. */
        record Replaced(MyReview review) implements Result {}

        /** Fields that break a rule of the review, each named once. */
        record InvalidReview(Set<ReviewForm.Field> fields) implements Result {

            public InvalidReview {
                fields = Set.copyOf(fields);
            }
        }

        /** The customer has no paid order that is not cancelled with a variant of the product. */
        record NotABuyer() implements Result {}

        /** There is no such product. */
        record ProductNotFound() implements Result {}

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
