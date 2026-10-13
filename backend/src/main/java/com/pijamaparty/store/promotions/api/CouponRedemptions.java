package com.pijamaparty.store.promotions.api;

import java.time.Instant;
import java.util.UUID;

/**
 * UC-15: a coupon gets a use when the order that applied it is paid (UC-10, UC-11). Joins the caller's transaction,
 * which must exist, so the use commits or rolls back with the order's payment.
 */
public interface CouponRedemptions {

    /**
     * The order used the coupon: one use, however many times it is called. A coupon deleted since the checkout gets
     * none.
     */
    void redeem(UUID couponId, UUID orderId, Instant at);
}
