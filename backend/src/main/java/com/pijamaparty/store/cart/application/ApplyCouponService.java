package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.PricedCart;
import com.pijamaparty.store.cart.domain.SellableVariant;
import com.pijamaparty.store.cart.domain.VariantId;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ApplyCouponService implements ApplyCoupon {

    private final LoadCart loadCart;
    private final SaveCart saveCart;
    private final VariantAvailability availability;
    private final Coupons coupons;
    private final ItemDescriptions descriptions;
    private final Clock clock;

    ApplyCouponService(LoadCart loadCart, SaveCart saveCart, VariantAvailability availability, Coupons coupons,
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
        if (found.isEmpty() || found.get().items().isEmpty()) {
            return new Result.EmptyCart();
        }
        Cart cart = found.get();
        Map<VariantId, SellableVariant> sellable = CartPricing.sellableIn(cart, availability);
        PricedCart priced = PricedCart.of(cart, sellable);
        Optional<CouponCheck> check = coupons.byCode(command.code(), priced.subtotal());
        if (check.isEmpty()) {
            return new Result.Unknown();
        }
        return switch (check.get().status()) {
            case APPLICABLE -> {
                cart.applyCoupon(check.get().id(), clock.instant());
                saveCart.save(cart);
                // UC-23: an automatic coupon that discounts more still wins over the code.
                yield new Result.Applied(CartPricing.view(cart, sellable, coupons, descriptions));
            }
            case INACTIVE -> new Result.Inactive();
            case NOT_STARTED -> new Result.NotStarted();
            case EXPIRED -> new Result.Expired();
            case USED_UP -> new Result.UsedUp();
            case BELOW_MINIMUM -> new Result.BelowMinimum(check.get().missing());
        };
    }
}
