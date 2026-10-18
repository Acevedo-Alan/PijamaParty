package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-24 (T-E5-06): whether the signed-in customer may review a product, and its review of it with its status
 * ({@code GET /api/account/reviews/{productId}}).
 */
public interface GetMyReview {

    Result execute(Query query);

    record Query(Actor actor, ProductId product) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(product, "product");
        }
    }

    sealed interface Result {

        /**
         * @param eligible whether the customer bought the product, so it may submit or edit its review; false for an
         *                 unknown product
         * @param review empty when the customer has not reviewed it
         */
        record Found(boolean eligible, Optional<MyReview> review) implements Result {

            public Found {
                Objects.requireNonNull(review, "review");
            }
        }

        /** The actor is not a customer. */
        record NotSignedIn() implements Result {}
    }
}
