package com.pijamaparty.store.ordering.adapter.out.promotions;

import com.pijamaparty.store.ordering.application.OrderCouponRedemptions;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.promotions.api.CouponRedemptions;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Counts a paid order's coupon use through the public API of the promotions module, in the caller's transaction. */
@Component
class PromotionsModuleCouponRedemptions implements OrderCouponRedemptions {

    private final CouponRedemptions redemptions;

    PromotionsModuleCouponRedemptions(CouponRedemptions redemptions) {
        this.redemptions = redemptions;
    }

    @Override
    public void redeem(UUID couponId, OrderId order, Instant at) {
        redemptions.redeem(couponId, order.value(), at);
    }
}
