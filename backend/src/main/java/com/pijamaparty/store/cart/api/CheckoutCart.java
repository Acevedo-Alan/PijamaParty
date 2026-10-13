package com.pijamaparty.store.cart.api;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CouponStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** UC-13: a cart as the checkout finds it. */
public sealed interface CheckoutCart {

    /** No cart, or a cart without items. */
    record Empty() implements CheckoutCart {}

    /** Some item is not {@code AVAILABLE}, or its price changed and the buyer has not acknowledged it (UC-09). */
    record NotReady() implements CheckoutCart {}

    /**
     * Every item can be bought at the price the buyer saw.
     *
     * @param lines in the order they were added, one per variant
     * @param discount the coupon's when it applies, otherwise zero
     * @param coupon the coupon the cart holds, whether or not it applies; empty when it has none or it no longer exists
     */
    record Ready(List<Line> lines, Money subtotal, Money discount, Optional<Coupon> coupon) implements CheckoutCart {

        public Ready {
            lines = List.copyOf(lines);
            Objects.requireNonNull(subtotal, "subtotal");
            Objects.requireNonNull(discount, "discount");
            Objects.requireNonNull(coupon, "coupon");
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("a ready cart has items");
            }
            if (!subtotal.equals(lines.stream().map(Line::amount).reduce(Money.zero(), Money::plus))) {
                throw new IllegalArgumentException("the subtotal is the sum of the lines");
            }
            if (discount.isGreaterThan(subtotal)) {
                throw new IllegalArgumentException("the discount is at most the subtotal");
            }
            if (!discount.isZero() && coupon.map(c -> c.status() != CouponStatus.APPLICABLE).orElse(true)) {
                throw new IllegalArgumentException("only an applicable coupon discounts");
            }
        }
    }

    /** A variant as the order keeps it (R-04): what it is, its current price and how many units. */
    record Line(UUID variantId, String sku, String productName, String size, String color, Money unitPrice,
                int quantity) {

        public Line {
            Objects.requireNonNull(variantId, "variantId");
            Objects.requireNonNull(sku, "sku");
            Objects.requireNonNull(productName, "productName");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(unitPrice, "unitPrice");
            if (quantity < 1) {
                throw new IllegalArgumentException("a line has at least one unit");
            }
        }

        public Money amount() {
            return unitPrice.times(quantity);
        }
    }

    /** UC-10: the cart's coupon and whether it applies now. {@code code} as stored, in upper case. */
    record Coupon(UUID couponId, String code, CouponStatus status) {

        public Coupon {
            Objects.requireNonNull(couponId, "couponId");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(status, "status");
        }
    }
}
