package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/** UC-09: removes an item from the actor's cart. */
public interface RemoveItem {

    Result execute(Command command);

    record Command(Actor actor, CartItemId itemId) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(itemId, "itemId");
        }
    }

    sealed interface Result {

        record Removed(CartView cart) implements Result {}

        /** No such item in the actor's cart. */
        record ItemNotFound() implements Result {}

        /** As in {@link AddItemToCart.Result.Forbidden}. */
        record Forbidden() implements Result {}
    }
}
