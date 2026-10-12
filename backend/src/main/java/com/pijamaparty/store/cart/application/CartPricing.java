package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartItem;
import com.pijamaparty.store.cart.domain.PricedCart;
import com.pijamaparty.store.cart.domain.SellableVariant;
import com.pijamaparty.store.cart.domain.VariantId;
import java.util.Map;

/** The current catalog state of a cart's variants, which every cart response is priced with (UC-09). */
final class CartPricing {

    private CartPricing() {}

    static Map<VariantId, SellableVariant> sellableIn(Cart cart, VariantAvailability availability) {
        return availability.sellable(cart.items().stream().map(CartItem::variantId).toList());
    }

    static CartView view(Cart cart, VariantAvailability availability) {
        return CartView.of(PricedCart.of(cart, sellableIn(cart, availability)));
    }
}
