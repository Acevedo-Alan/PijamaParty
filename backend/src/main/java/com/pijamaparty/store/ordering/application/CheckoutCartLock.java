package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CouponStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: the cart the checkout sells, as the cart module prices it (UC-09, UC-10, UC-13). */
public interface CheckoutCartLock {

    /**
     * The actor's cart, locked until the caller's transaction ends, which runs as {@code actor}. An actor that cannot
     * own a cart has none.
     */
    CartState lock(Actor actor);

    sealed interface CartState {

        /** No cart, or a cart without items. */
        record Empty() implements CartState {}

        /** An item is not available, or has a price change the buyer has not acknowledged. */
        record NotReady() implements CartState {}

        /**
         * @param lines in the cart's order, one per variant
         * @param discount the coupon's when it applies, otherwise zero
         * @param coupon the cart's coupon, whether or not it applies
         */
        record Ready(List<OrderLine> lines, Money subtotal, Money discount, Optional<CartCoupon> coupon)
            implements CartState {

            public Ready {
                lines = List.copyOf(lines);
                Objects.requireNonNull(subtotal, "subtotal");
                Objects.requireNonNull(discount, "discount");
                Objects.requireNonNull(coupon, "coupon");
                if (lines.isEmpty()) {
                    throw new IllegalArgumentException("a ready cart has items");
                }
            }
        }
    }

    /** {@code code} as stored, in upper case. */
    record CartCoupon(UUID couponId, String code, CouponStatus status) {

        public CartCoupon {
            Objects.requireNonNull(couponId, "couponId");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(status, "status");
        }

        public boolean applies() {
            return status == CouponStatus.APPLICABLE;
        }
    }
}
