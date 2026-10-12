package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartItem;
import com.pijamaparty.store.cart.domain.PricedCart;
import com.pijamaparty.store.cart.domain.SellableVariant;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Map;
import java.util.Optional;

/**
 * The current catalog state of a cart's variants and its coupon, which every cart response is priced with (UC-09,
 * UC-10).
 */
final class CartPricing {

    private CartPricing() {}

    static Map<VariantId, SellableVariant> sellableIn(Cart cart, VariantAvailability availability) {
        return availability.sellable(cart.items().stream().map(CartItem::variantId).toList());
    }

    static CartView view(Cart cart, VariantAvailability availability, Coupons coupons) {
        return view(cart, sellableIn(cart, availability), coupons);
    }

    /** The coupon is evaluated again on the current subtotal: one that no longer applies discounts nothing. */
    static CartView view(Cart cart, Map<VariantId, SellableVariant> sellable, Coupons coupons) {
        PricedCart priced = PricedCart.of(cart, sellable);
        Optional<CouponCheck> coupon = cart.coupon().flatMap(id -> coupons.byId(id, priced.subtotal()));
        return discounted(priced, coupon);
    }

    /** {@code priced} with the discount of {@code coupon} when it applies. */
    static CartView discounted(PricedCart priced, Optional<CouponCheck> coupon) {
        Money discount = coupon.filter(CouponCheck::applies).map(CouponCheck::discount).orElse(Money.zero());
        return CartView.of(priced.withDiscount(discount), coupon);
    }
}
