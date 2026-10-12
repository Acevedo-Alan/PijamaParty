package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartOwner;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RemoveItemService implements RemoveItem {

    private final LoadCart loadCart;
    private final SaveCart saveCart;
    private final VariantAvailability availability;
    private final Clock clock;

    RemoveItemService(LoadCart loadCart, SaveCart saveCart, VariantAvailability availability, Clock clock) {
        this.loadCart = loadCart;
        this.saveCart = saveCart;
        this.availability = availability;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        Optional<CartOwner> owner = CartOwner.of(command.actor());
        if (owner.isEmpty()) {
            return new Result.Forbidden();
        }
        Optional<Cart> cart = loadCart.lockByOwner(owner.get());
        if (cart.isEmpty() || !cart.get().remove(command.itemId(), clock.instant())) {
            return new Result.ItemNotFound();
        }
        saveCart.save(cart.get());
        return new Result.Removed(CartPricing.view(cart.get(), availability));
    }
}
