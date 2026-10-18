package com.pijamaparty.store.ordering.adapter.out.cart;

import com.pijamaparty.store.cart.api.CheckoutCart;
import com.pijamaparty.store.cart.api.CheckoutCarts;
import com.pijamaparty.store.ordering.application.CheckoutCartLock;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.platform.domain.Actor;
import org.springframework.stereotype.Component;

/** The cart the checkout sells, through the public API of the cart module, in the caller's transaction. */
@Component
class CartModuleCheckoutCartLock implements CheckoutCartLock {

    private final CheckoutCarts checkoutCarts;

    CartModuleCheckoutCartLock(CheckoutCarts checkoutCarts) {
        this.checkoutCarts = checkoutCarts;
    }

    @Override
    public CartState lock(Actor actor) {
        return switch (checkoutCarts.lock(actor)) {
            case CheckoutCart.Empty empty -> new CartState.Empty();
            case CheckoutCart.NotReady notReady -> new CartState.NotReady();
            case CheckoutCart.Ready ready -> new CartState.Ready(
                ready.lines().stream().map(CartModuleCheckoutCartLock::toOrderLine).toList(),
                ready.subtotal(),
                ready.coupon().map(coupon -> new CartCoupon(coupon.couponId(), coupon.code(), coupon.status())),
                ready.applied().map(applied -> new CheckoutCartLock.CartDiscount(applied.couponId(), applied.code(),
                    applied.amount())));
        };
    }

    private static OrderLine toOrderLine(CheckoutCart.Line line) {
        return new OrderLine(line.variantId(), line.sku(), line.productName(), line.size(), line.color(),
            line.unitPrice(), line.quantity());
    }
}
