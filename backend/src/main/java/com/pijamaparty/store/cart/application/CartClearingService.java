package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.api.CartClearing;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.platform.domain.Actor;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC-13: the checkout empties the cart it turned into an order. */
@Service
class CartClearingService implements CartClearing {

    private final LoadCart loadCart;
    private final SaveCart saveCart;
    private final Clock clock;

    CartClearingService(LoadCart loadCart, SaveCart saveCart, Clock clock) {
        this.loadCart = loadCart;
        this.saveCart = saveCart;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void clear(Actor actor) {
        Optional<Cart> cart = CartOwner.of(actor).flatMap(loadCart::lockByOwner);
        if (cart.isPresent() && cart.get().clear(clock.instant())) {
            saveCart.save(cart.get());
        }
    }
}
