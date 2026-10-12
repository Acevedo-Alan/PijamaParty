package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartItem;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;

/**
 * The full state of a cart that every cart response returns (UC-08, UC-09): its items in the order they were added.
 * Totals and the price and stock warnings come with T-E2-03.
 */
public record CartView(List<ItemView> items) {

    private static final CartView EMPTY = new CartView(List.of());

    public CartView {
        items = List.copyOf(items);
    }

    public static CartView empty() {
        return EMPTY;
    }

    public static CartView of(Cart cart) {
        return new CartView(cart.items().stream().map(ItemView::of).toList());
    }

    /** {@code unitPrice} is the price the customer last saw. */
    public record ItemView(CartItemId id, VariantId variantId, int quantity, Money unitPrice) {

        static ItemView of(CartItem item) {
            return new ItemView(item.id(), item.variantId(), item.quantity(), item.lastSeenPrice());
        }
    }
}
