package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-19 (T-E4-04): a customer who signs in with the cart token of its guest cart gets that cart joined to its own, and
 * the guest cart is deleted. It never fails the sign-in: a line it cannot bring is left out.
 */
public interface MergeGuestCart {

    Result execute(Command command);

    /** {@code actor} is the customer signing in, carrying the cart token of the browser. */
    record Command(Actor actor) {

        public Command {
            Objects.requireNonNull(actor, "actor");
        }
    }

    sealed interface Result {

        /** The guest cart joined the customer's and is gone. */
        record Merged() implements Result {}

        /** No guest cart for the token, or the actor is not a customer carrying one. */
        record NoGuestCart() implements Result {}
    }
}
