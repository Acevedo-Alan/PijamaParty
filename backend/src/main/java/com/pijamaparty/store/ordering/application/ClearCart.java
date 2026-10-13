package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Actor;

/** Outbound port: empties the actor's cart once its order is placed (UC-13), within the caller's transaction. */
@FunctionalInterface
public interface ClearCart {

    /** Removes every item and the coupon of the actor's cart; without a cart it does nothing. */
    void clear(Actor actor);
}
