package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.domain.CouponId;
import java.time.Instant;
import java.util.UUID;

/** UC-15: records that a paid order used a coupon, in the caller's transaction. Counted by {@link CouponUses}. */
public interface SaveCouponRedemption {

    /**
     * One use of the coupon by the order, once (RNF-20).
     *
     * @return false when the order already used it, or the coupon no longer exists
     */
    boolean redeem(CouponId coupon, UUID orderId, Instant at);
}
