package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-09: the customer saw the price changes of the cart, so they are no longer announced. Every item the shop sells
 * remembers its current price. Never creates a cart.
 */
public interface AcknowledgePriceChanges {

    Result execute(Command command);

    record Command(Actor actor) {

        public Command {
            Objects.requireNonNull(actor, "actor");
        }
    }

    sealed interface Result {

        /** The full cart, empty when the actor has none. */
        record Acknowledged(CartView cart) implements Result {}

        /** As in {@link AddItemToCart.Result.Forbidden}. */
        record Forbidden() implements Result {}
    }
}
