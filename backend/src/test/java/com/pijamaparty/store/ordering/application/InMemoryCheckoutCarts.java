package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake of {@link CheckoutCartLock} and {@link ClearCart}: the state each actor's cart is in, as the cart module would
 * price it. Locks are not modeled.
 */
public class InMemoryCheckoutCarts implements CheckoutCartLock, ClearCart {

    private final Map<Actor, CartState> carts = new ConcurrentHashMap<>();
    private final Set<Actor> cleared = ConcurrentHashMap.newKeySet();

    /** Test arrangement: {@code actor}'s cart is in {@code state}. */
    public void put(Actor actor, CartState state) {
        carts.put(actor, state);
    }

    /** Test inspection: whether the actor's cart was cleared. */
    public boolean wasCleared(Actor actor) {
        return cleared.contains(actor);
    }

    @Override
    public CartState lock(Actor actor) {
        return carts.getOrDefault(actor, new CartState.Empty());
    }

    @Override
    public void clear(Actor actor) {
        if (carts.containsKey(actor)) {
            carts.put(actor, new CartState.Empty());
            cleared.add(actor);
        }
    }
}
