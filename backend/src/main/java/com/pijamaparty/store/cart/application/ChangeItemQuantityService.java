package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartItem;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.SellableVariant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ChangeItemQuantityService implements ChangeItemQuantity {

    private final LoadCart loadCart;
    private final SaveCart saveCart;
    private final VariantAvailability availability;
    private final Coupons coupons;
    private final Clock clock;

    ChangeItemQuantityService(LoadCart loadCart, SaveCart saveCart, VariantAvailability availability, Coupons coupons,
                              Clock clock) {
        this.loadCart = loadCart;
        this.saveCart = saveCart;
        this.availability = availability;
        this.coupons = coupons;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        Optional<CartOwner> owner = CartOwner.of(command.actor());
        if (owner.isEmpty()) {
            return new Result.Forbidden();
        }
        Optional<Cart> found = loadCart.lockByOwner(owner.get());
        Optional<CartItem> item = found.flatMap(cart -> cart.item(command.itemId()));
        if (item.isEmpty()) {
            return new Result.ItemNotFound();
        }
        Cart cart = found.get();
        // Removing needs no stock; a variant the catalog no longer sells has none.
        int available = command.quantity() == 0 ? 0 : availability.of(item.get().variantId())
            .map(SellableVariant::available)
            .orElse(0);
        return switch (cart.changeQuantity(command.itemId(), command.quantity(), available, clock.instant())) {
            case Cart.ChangeOutcome.Changed changed -> saved(cart);
            case Cart.ChangeOutcome.Removed removed -> saved(cart);
            case Cart.ChangeOutcome.ExceedsStock exceeds ->
                new Result.InsufficientStock(item.get().variantId(), exceeds.available());
            case Cart.ChangeOutcome.NoSuchItem missing -> new Result.ItemNotFound();
        };
    }

    private Result saved(Cart cart) {
        saveCart.save(cart);
        return new Result.Changed(CartPricing.view(cart, availability, coupons));
    }
}
