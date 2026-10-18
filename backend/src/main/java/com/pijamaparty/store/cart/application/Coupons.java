package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** UC-10, UC-23: coupons and a cart's discount, as promotions decides. Read only. */
public interface Coupons {

    /**
     * The code coupon of the code a customer typed, ignoring surrounding spaces and case; empty when there is none. An
     * automatic coupon's code is unknown.
     */
    Optional<CouponCheck> byCode(String code, Money subtotal);

    /**
     * The cart's discount: its code coupon evaluated as UC-10 shows it, and the one coupon that discounts, which may be
     * an automatic one (UC-23).
     *
     * @param lines the cart's {@code AVAILABLE} lines
     * @param coupon the code coupon the cart holds, if any
     */
    CartDiscount discount(List<Line> lines, Optional<CouponId> coupon);

    /**
     * An {@code AVAILABLE} cart line as a coupon's scope sees it: the catalog's product and category.
     *
     * @param amount its price times its quantity
     */
    record Line(UUID productId, UUID categoryId, Money amount) {

        public Line {
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(categoryId, "categoryId");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
