package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.time.Instant;
import java.util.UUID;

/** Outbound port: a paid order's coupon gets its use (UC-15, UC-10), within the caller's transaction. */
public interface OrderCouponRedemptions {

    /** Once per coupon and order, however many times it is called; a coupon deleted meanwhile gets none. */
    void redeem(UUID couponId, OrderId order, Instant at);
}
