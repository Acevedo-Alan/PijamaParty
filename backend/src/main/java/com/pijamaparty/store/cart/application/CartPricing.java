package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartItem;
import com.pijamaparty.store.cart.domain.PricedCart;
import com.pijamaparty.store.cart.domain.SellableVariant;
import com.pijamaparty.store.cart.domain.VariantId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The current catalog state of a cart's variants and its discount, which every cart response is priced with (UC-09,
 * UC-10, UC-23), and what each line shows of its variant (UC-08).
 */
final class CartPricing {

    private CartPricing() {}

    static Map<VariantId, SellableVariant> sellableIn(Cart cart, VariantAvailability availability) {
        return availability.sellable(cart.items().stream().map(CartItem::variantId).toList());
    }

    static CartView view(Cart cart, VariantAvailability availability, Coupons coupons,
                         ItemDescriptions descriptions) {
        return view(cart, sellableIn(cart, availability), coupons, descriptions);
    }

    /** The discount is priced again on the current lines: a coupon that no longer applies discounts nothing. */
    static CartView view(Cart cart, Map<VariantId, SellableVariant> sellable, Coupons coupons,
                         ItemDescriptions descriptions) {
        Discounted discounted = priced(cart, sellable, coupons);
        var variants = discounted.cart().lines().stream().map(line -> line.item().variantId()).toList();
        return CartView.of(discounted.cart(), discounted.discount(),
            variants.isEmpty() ? Map.of() : descriptions.describe(variants));
    }

    /** {@code cart} priced now, with its discount; without line descriptions. */
    static Discounted priced(Cart cart, VariantAvailability availability, Coupons coupons) {
        return priced(cart, sellableIn(cart, availability), coupons);
    }

    /**
     * UC-23: the discount is priced on the {@code AVAILABLE} lines, the ones the subtotal counts, with the catalog's
     * product and category of each.
     */
    static Discounted priced(Cart cart, Map<VariantId, SellableVariant> sellable, Coupons coupons) {
        PricedCart priced = PricedCart.of(cart, sellable);
        List<Coupons.Line> lines = priced.lines().stream()
            .filter(line -> line.status() == PricedCart.LineStatus.AVAILABLE)
            .map(line -> {
                // An available line is of a variant the catalog sells.
                SellableVariant variant = sellable.get(line.item().variantId());
                return new Coupons.Line(variant.productId(), variant.categoryId(),
                    line.unitPrice().times(line.item().quantity()));
            })
            .toList();
        CartDiscount discount = coupons.discount(lines, cart.coupon());
        return new Discounted(priced.withDiscount(discount.amount()), discount);
    }

    /** A cart priced with its discount, which {@code cart} already takes off. */
    record Discounted(PricedCart cart, CartDiscount discount) {

        Discounted {
            Objects.requireNonNull(cart, "cart");
            Objects.requireNonNull(discount, "discount");
        }
    }
}
