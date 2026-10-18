package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.api.CheckoutCart;
import com.pijamaparty.store.cart.api.CheckoutCarts;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.PricedCart;
import com.pijamaparty.store.cart.domain.SellableVariant;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.promotions.api.CouponStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC-13: the actor's cart locked and priced as every cart response (UC-09, UC-10, UC-23), for the checkout. */
@Service
class CheckoutCartsService implements CheckoutCarts {

    private final LoadCart loadCart;
    private final VariantAvailability availability;
    private final Coupons coupons;
    private final ItemDescriptions descriptions;

    CheckoutCartsService(LoadCart loadCart, VariantAvailability availability, Coupons coupons,
                         ItemDescriptions descriptions) {
        this.loadCart = loadCart;
        this.availability = availability;
        this.coupons = coupons;
        this.descriptions = descriptions;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CheckoutCart lock(Actor actor) {
        Optional<Cart> found = CartOwner.of(actor).flatMap(loadCart::lockByOwner);
        if (found.isEmpty() || found.get().items().isEmpty()) {
            return new CheckoutCart.Empty();
        }
        Cart cart = found.get();
        Map<VariantId, SellableVariant> sellable = CartPricing.sellableIn(cart, availability);
        if (!PricedCart.of(cart, sellable).isReadyForCheckout()) {
            return new CheckoutCart.NotReady();
        }
        CartPricing.Discounted priced = CartPricing.priced(cart, sellable, coupons);
        Map<VariantId, ItemDescription> described =
            descriptions.describe(priced.cart().lines().stream().map(line -> line.item().variantId()).toList());
        List<CheckoutCart.Line> lines = priced.cart().lines().stream()
            .map(line -> line(line, described.get(line.item().variantId())))
            .toList();
        CartDiscount discount = priced.discount();
        return new CheckoutCart.Ready(lines, priced.cart().subtotal(),
            discount.coupon().map(check -> new CheckoutCart.Coupon(check.id().value(), check.code(),
                CouponStatus.valueOf(check.status().name()))),
            discount.applied().map(applied ->
                new CheckoutCart.AppliedDiscount(applied.id().value(), applied.code(), applied.amount())));
    }

    private static CheckoutCart.Line line(PricedCart.PricedLine line, ItemDescription description) {
        if (description == null) {
            // The catalog sells it, so it knows it.
            throw new IllegalStateException("no description of sellable variant " + line.item().variantId().value());
        }
        return new CheckoutCart.Line(line.item().variantId().value(), description.sku(), description.productName(),
            description.size(), description.color(), line.unitPrice(), line.item().quantity());
    }
}
