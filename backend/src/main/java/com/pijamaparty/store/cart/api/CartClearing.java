package com.pijamaparty.store.cart.api;

import com.pijamaparty.store.platform.domain.Actor;

/** UC-13: a placed order empties the cart it came from. Joins the caller's transaction, which must exist. */
public interface CartClearing {

    /** Removes every item and the coupon of the actor's cart; without a cart it does nothing. */
    void clear(Actor actor);
}
