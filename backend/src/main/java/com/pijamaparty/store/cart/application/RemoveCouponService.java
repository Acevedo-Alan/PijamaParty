package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartOwner;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RemoveCouponService implements RemoveCoupon {

    private final LoadCart loadCart;
    private final SaveCart saveCart;
    private final VariantAvailability availability;
    private final Coupons coupons;
    private final ItemDescriptions descriptions;
    private final Clock clock;

    RemoveCouponService(LoadCart loadCart, SaveCart saveCart, VariantAvailability availability, Coupons coupons,
                        ItemDescriptions descriptions, Clock clock) {
        this.loadCart = loadCart;
        this.saveCart = saveCart;
        this.availability = availability;
        this.coupons = coupons;
        this.descriptions = descriptions;
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
            return new Result.Removed(CartView.empty());
        }
        Cart cart = found.get();
        if (cart.removeCoupon(clock.instant())) {
            saveCart.save(cart);
        }
        return new Result.Removed(CartPricing.view(cart, availability, coupons, descriptions));
    }
}
