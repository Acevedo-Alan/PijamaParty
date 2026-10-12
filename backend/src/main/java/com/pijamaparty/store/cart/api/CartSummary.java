package com.pijamaparty.store.cart.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/**
 * UC-09, UC-13: the amounts of a cart that can be bought, priced on the server with the current catalog prices and
 * stock: only its available lines count in the subtotal, and the discount is its coupon's when it applies.
 *
 * @param total the subtotal after the discount, before shipping
 */
public record CartSummary(Money subtotal, Money discount, Money total) {

    public CartSummary {
        Objects.requireNonNull(subtotal, "subtotal");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(total, "total");
        if (discount.isGreaterThan(subtotal) || !total.equals(subtotal.minus(discount))) {
            throw new IllegalArgumentException("the discount is at most the subtotal, and the total is what is left");
        }
    }
}
