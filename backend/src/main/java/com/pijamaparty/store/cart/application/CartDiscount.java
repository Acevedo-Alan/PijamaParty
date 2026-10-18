package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-10, UC-23: a cart's discount. Discounts never stack: one coupon discounts, the one that takes the most.
 *
 * @param coupon the code coupon the cart holds, evaluated, whether or not it is the one that discounts; empty when it
 *               holds none or it no longer exists
 * @param applied the coupon that discounts, if any: the held code coupon or an automatic one
 */
public record CartDiscount(Optional<CouponCheck> coupon, Optional<Applied> applied) {

    public static final CartDiscount NONE = new CartDiscount(Optional.empty(), Optional.empty());

    public CartDiscount {
        Objects.requireNonNull(coupon, "coupon");
        Objects.requireNonNull(applied, "applied");
    }

    /** What comes off the subtotal: the applied coupon's discount, or zero. */
    public Money amount() {
        return applied.map(Applied::amount).orElse(Money.zero());
    }

    /**
     * @param code as stored, in upper case; it labels the discount ("Descuento VERANO10")
     * @param automatic whether it applied on its own (UC-23) rather than by the held code
     */
    public record Applied(CouponId id, String code, boolean automatic, Money amount) {

        public Applied {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
