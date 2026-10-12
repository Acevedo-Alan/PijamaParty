package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link CartAmounts}: the amounts each actor's cart sells, as the cart module would price them. */
public class InMemoryCartAmounts implements CartAmounts {

    private final Map<Actor, Amounts> carts = new ConcurrentHashMap<>();

    /** {@code actor}'s cart, with at least one available line, sells {@code amounts}. */
    public void put(Actor actor, Amounts amounts) {
        carts.put(actor, amounts);
    }

    @Override
    public Optional<Amounts> of(Actor actor) {
        return Optional.ofNullable(carts.get(actor));
    }
}
