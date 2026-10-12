package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-09: changes the quantity of an item of the actor's cart, revalidating the stock; 0 removes it. A variant the
 * catalog no longer sells has nothing available.
 */
public interface ChangeItemQuantity {

    Result execute(Command command);

    /** {@code quantity} from 0 to {@link Cart#MAX_QUANTITY}. */
    record Command(Actor actor, CartItemId itemId, int quantity) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(itemId, "itemId");
            if (!Cart.isValidChangedQuantity(quantity)) {
                throw new IllegalArgumentException("quantity out of range: " + quantity);
            }
        }
    }

    sealed interface Result {

        /** Changed, or removed for quantity 0. */
        record Changed(CartView cart) implements Result {}

        /** The new quantity is above the stock; {@code available} units are left. */
        record InsufficientStock(VariantId variantId, int available) implements Result {}

        /** No such item in the actor's cart. */
        record ItemNotFound() implements Result {}

        /** As in {@link AddItemToCart.Result.Forbidden}. */
        record Forbidden() implements Result {}
    }
}
