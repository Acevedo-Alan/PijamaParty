package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.SellableVariant;
import com.pijamaparty.store.cart.domain.VariantId;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AcknowledgePriceChangesService implements AcknowledgePriceChanges {

    private final LoadCart loadCart;
    private final SaveCart saveCart;
    private final VariantAvailability availability;
    private final Coupons coupons;
    private final Clock clock;

    AcknowledgePriceChangesService(LoadCart loadCart, SaveCart saveCart, VariantAvailability availability,
                                   Coupons coupons, Clock clock) {
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
        if (found.isEmpty()) {
            return new Result.Acknowledged(CartView.empty());
        }
        Cart cart = found.get();
        Map<VariantId, SellableVariant> sellable = CartPricing.sellableIn(cart, availability);
        if (cart.acknowledgePrices(sellable, clock.instant())) {
            saveCart.save(cart);
        }
        return new Result.Acknowledged(CartPricing.view(cart, sellable, coupons));
    }
}
