package com.pijamaparty.store.promotions.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-10: whether a code coupon applies to a cart's subtotal today (store date, R-07) and its discount, calculated on
 * the server. Automatic coupons (UC-23) are unknown here: they apply on their own, through {@link CartDiscounts}. Read
 * only: a use is counted when an order is confirmed (E3), not here. Joins the caller's transaction.
 */
public interface CouponDiscounts {

    /**
     * The code coupon of the code a customer typed, ignoring surrounding spaces and case; empty when no code coupon has
     * it or the text cannot be a code.
     */
    Optional<CouponDiscount> byCode(String code, Money subtotal);

    /** The code coupon a cart holds; empty when it no longer exists or is automatic. */
    Optional<CouponDiscount> byId(UUID couponId, Money subtotal);
}
