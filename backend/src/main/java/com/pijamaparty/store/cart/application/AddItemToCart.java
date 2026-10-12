package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-08: adds units of a variant to the actor's cart, to its line if it has one, creating the cart with the first
 * product. The quantity in the cart never exceeds the available stock.
 */
public interface AddItemToCart {

    Result execute(Command command);

    /** {@code quantity} from 1 to {@link Cart#MAX_QUANTITY}. */
    record Command(Actor actor, VariantId variantId, int quantity) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(variantId, "variantId");
            if (!Cart.isValidAddedQuantity(quantity)) {
                throw new IllegalArgumentException("quantity out of range: " + quantity);
            }
        }
    }

    sealed interface Result {

        record Added(CartView cart) implements Result {}

        /** The line would exceed the stock; {@code available} units are left. */
        record InsufficientStock(VariantId variantId, int available) implements Result {}

        /** Unknown, or not sold by the catalog. */
        record VariantUnavailable(VariantId variantId) implements Result {}

        /** The actor cannot own a cart: the administrator, or a guest without a cart token. */
        record Forbidden() implements Result {}
    }
}
