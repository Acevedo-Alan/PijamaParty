package com.pijamaparty.store.cart.api;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Optional;

/** UC-13: the cart the checkout sells. Read only; joins the caller's transaction, which runs as {@code actor}. */
public interface CartSummaries {

    /**
     * The actor's cart; empty when it has none, none of its lines is available, or it cannot own a cart (the
     * administrator, the system and a guest without a cart token).
     */
    Optional<CartSummary> of(Actor actor);
}
