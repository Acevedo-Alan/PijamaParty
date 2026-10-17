package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;

/**
 * UC-28 (T-E4-16): a deleted customer's cart goes with its account, items and coupon included. Idempotent: a second
 * run finds no cart.
 */
public interface DeleteCustomerCart {

    Result execute(Command command);

    record Command(UserId customer) {

        public Command {
            Objects.requireNonNull(customer, "customer");
        }
    }

    sealed interface Result {

        record Deleted() implements Result {}

        /** The customer had no cart. */
        record NoCart() implements Result {}
    }
}
