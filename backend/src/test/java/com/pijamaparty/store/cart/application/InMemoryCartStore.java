package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.platform.testing.InMemoryStore;
import java.util.Optional;

/** Fake of {@link LoadCart} and {@link SaveCart}: one cart per owner, as the unique keys of V12. Locks are not modeled. */
public class InMemoryCartStore implements LoadCart, SaveCart {

    private final InMemoryStore<CartId, Cart> carts = InMemoryStore.copying(Cart::id, Cart::copy);

    @Override
    public Optional<Cart> findByOwner(CartOwner owner) {
        return carts.findAll(cart -> cart.owner().equals(owner)).stream().findFirst();
    }

    @Override
    public Optional<Cart> lockByOwner(CartOwner owner) {
        return findByOwner(owner);
    }

    @Override
    public synchronized void save(Cart cart) {
        if (!carts.findAll(other -> other.owner().equals(cart.owner()) && !other.id().equals(cart.id())).isEmpty()) {
            throw new IllegalStateException("the owner already has another cart");
        }
        carts.save(cart);
    }

    public int size() {
        return carts.size();
    }
}
