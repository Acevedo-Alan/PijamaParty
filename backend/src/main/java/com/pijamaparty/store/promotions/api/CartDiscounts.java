package com.pijamaparty.store.promotions.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-10, UC-23: the discount of a cart today (store date, R-07), calculated on the server. It evaluates the cart's code
 * coupon and every active automatic coupon, and only the largest discount applies: on a tie the code coupon, then the
 * oldest automatic one. Read only: a use is counted when the order is paid ({@link CouponRedemptions}). Joins the
 * caller's transaction.
 */
public interface CartDiscounts {

    /**
     * @param lines the cart's {@code AVAILABLE} lines (UC-09)
     * @param couponId the code coupon the cart holds, if any; one that no longer exists, or an automatic one, is none
     */
    CartDiscount price(List<Line> lines, Optional<UUID> couponId);

    /**
     * An {@code AVAILABLE} cart line. The ids are the catalog's, compared only with a coupon's scope.
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
