package com.pijamaparty.store.promotions.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-10, UC-23: a cart's discount.
 *
 * @param coupon the cart's code coupon evaluated as UC-10 shows it, whether or not it is the one that discounts; empty
 *               when the cart holds none
 * @param applied the one coupon that discounts, if any: the held code coupon or an automatic one
 */
public record CartDiscount(Optional<CouponDiscount> coupon, Optional<Applied> applied) {

    public CartDiscount {
        Objects.requireNonNull(coupon, "coupon");
        Objects.requireNonNull(applied, "applied");
    }

    /** What comes off the subtotal: the applied coupon's discount, or zero. */
    public Money amount() {
        return applied.map(Applied::amount).orElse(Money.zero());
    }

    /**
     * @param code as stored, in upper case; an automatic coupon's is its label ("Descuento VERANO10")
     * @param amount at most the matching subtotal
     */
    public record Applied(UUID couponId, String code, boolean automatic, Money amount) {

        public Applied {
            Objects.requireNonNull(couponId, "couponId");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
