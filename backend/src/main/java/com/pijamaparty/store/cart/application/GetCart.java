package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/** UC-09: the actor's cart; empty when it has none or cannot own one. Never creates a cart. */
public interface GetCart {

    CartView execute(Query query);

    record Query(Actor actor) {

        public Query {
            Objects.requireNonNull(actor, "actor");
        }
    }
}
