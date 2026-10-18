package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;

/**
 * UC-28 (T-E5-06): a deleted customer's reviews go with its account, so they no longer show or count on the product
 * page. Idempotent: a second run finds no review.
 */
public interface DeleteCustomerReviews {

    Result execute(Command command);

    record Command(UserId customer) {

        public Command {
            Objects.requireNonNull(customer, "customer");
        }
    }

    sealed interface Result {

        record Deleted(int reviews) implements Result {}

        /** The customer had no review. */
        record NoReviews() implements Result {}
    }
}
