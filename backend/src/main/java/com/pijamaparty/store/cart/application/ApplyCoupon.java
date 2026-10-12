package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/**
 * UC-10: applies the coupon of a code to the actor's cart, replacing the one it had, when promotions finds that it
 * applies to the cart's subtotal. A rejected code leaves the cart as it was. Never creates a cart.
 */
public interface ApplyCoupon {

    Result execute(Command command);

    /** {@code code} as the customer typed it. */
    record Command(Actor actor, String code) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(code, "code");
        }
    }

    sealed interface Result {

        /** The full cart with the coupon's discount. */
        record Applied(CartView cart) implements Result {}

        /** No coupon has this code. */
        record Unknown() implements Result {}

        record Inactive() implements Result {}

        record NotStarted() implements Result {}

        /** "El cupón venció". */
        record Expired() implements Result {}

        /** All its uses are taken. */
        record UsedUp() implements Result {}

        /** The cart's subtotal needs {@code missing} more to reach the coupon's minimum purchase. */
        record BelowMinimum(Money missing) implements Result {}

        /** The actor has no cart, or its cart has no items. */
        record EmptyCart() implements Result {}

        /** As in {@link AddItemToCart.Result.Forbidden}. */
        record Forbidden() implements Result {}
    }
}
