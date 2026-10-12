package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/** UC-10: takes the coupon off the actor's cart. Without one it changes nothing. Never creates a cart. */
public interface RemoveCoupon {

    Result execute(Command command);

    record Command(Actor actor) {

        public Command {
            Objects.requireNonNull(actor, "actor");
        }
    }

    sealed interface Result {

        /** The full cart without a coupon, empty when the actor has none. */
        record Removed(CartView cart) implements Result {}

        /** As in {@link AddItemToCart.Result.Forbidden}. */
        record Forbidden() implements Result {}
    }
}
