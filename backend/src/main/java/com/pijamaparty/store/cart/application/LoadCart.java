package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartOwner;
import java.util.Optional;

/** Outbound port: reads the cart of an owner, with its items, within the caller's transaction. */
public interface LoadCart {

    Optional<Cart> findByOwner(CartOwner owner);

    /**
     * For a change: also locks the cart until the transaction ends ({@code SELECT ... FOR UPDATE}), so two changes of
     * one cart run one after the other (UC-09: the last one wins).
     */
    Optional<Cart> lockByOwner(CartOwner owner);
}
