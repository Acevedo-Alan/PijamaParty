package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.domain.CouponId;

/**
 * Outbound port: deletes a coupon within the caller's transaction (UC-11). A coupon with uses is not deleted, and the
 * caller's transaction stays usable.
 */
public interface RemoveStoredCoupon {

    Outcome remove(CouponId id);

    enum Outcome {
        REMOVED,
        NOT_FOUND,
        HAS_USES
    }
}
