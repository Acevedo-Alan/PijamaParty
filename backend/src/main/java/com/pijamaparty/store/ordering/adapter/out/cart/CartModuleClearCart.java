package com.pijamaparty.store.ordering.adapter.out.cart;

import com.pijamaparty.store.cart.api.CartClearing;
import com.pijamaparty.store.ordering.application.ClearCart;
import com.pijamaparty.store.platform.domain.Actor;
import org.springframework.stereotype.Component;

/** Empties the actor's cart through the public API of the cart module, in the caller's transaction. */
@Component
class CartModuleClearCart implements ClearCart {

    private final CartClearing cartClearing;

    CartModuleClearCart(CartClearing cartClearing) {
        this.cartClearing = cartClearing;
    }

    @Override
    public void clear(Actor actor) {
        cartClearing.clear(actor);
    }
}
