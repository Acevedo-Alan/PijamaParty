package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;

/** Outbound port: writes a cart within the caller's transaction. */
public interface SaveCart {

    /** Creates the cart or replaces its state: its items become exactly those of {@code cart}. */
    void save(Cart cart);
}
