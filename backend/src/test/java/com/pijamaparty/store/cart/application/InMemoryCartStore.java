package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.platform.testing.InMemoryStore;
import java.time.Instant;
import java.util.Optional;

/**
 * Fake of {@link LoadCart}, {@link SaveCart}, {@link DeleteCart} and {@link DeleteIdleCarts}: one cart per owner, as
 * the unique keys of V12. Locks and Row Level Security are not modeled.
 */
public class InMemoryCartStore implements LoadCart, SaveCart, DeleteCart, DeleteIdleCarts {

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

    @Override
    public synchronized boolean delete(CartId cartId) {
        return carts.delete(cartId);
    }

    @Override
    public synchronized int deleteUpdatedBefore(Instant cutoff) {
        var idle = carts.findAll(cart -> cart.updatedAt().isBefore(cutoff));
        idle.forEach(cart -> carts.delete(cart.id()));
        return idle.size();
    }

    public int size() {
        return carts.size();
    }
}
