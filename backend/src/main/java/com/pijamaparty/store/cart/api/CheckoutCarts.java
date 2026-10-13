package com.pijamaparty.store.cart.api;

import com.pijamaparty.store.platform.domain.Actor;

/** UC-13: the cart the checkout turns into an order. Joins the caller's transaction, which must exist. */
public interface CheckoutCarts {

    /**
     * The actor's cart, locked until the transaction ends so two checkouts of it run one after the other, and priced
     * now as UC-09: {@link CheckoutCart.Empty} without a cart or items (also for an actor that cannot own a cart),
     * {@link CheckoutCart.NotReady} while an item is not available or has an unacknowledged price change.
     */
    CheckoutCart lock(Actor actor);
}
