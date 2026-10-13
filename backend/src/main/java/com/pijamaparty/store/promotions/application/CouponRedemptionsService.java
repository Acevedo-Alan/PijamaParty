package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.api.CouponRedemptions;
import com.pijamaparty.store.promotions.domain.CouponId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC-15: counts the use of a coupon by a paid order. */
@Service
class CouponRedemptionsService implements CouponRedemptions {

    private final SaveCouponRedemption redemptions;

    CouponRedemptionsService(SaveCouponRedemption redemptions) {
        this.redemptions = redemptions;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void redeem(UUID couponId, UUID orderId, Instant at) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(at, "at");
        redemptions.redeem(CouponId.of(couponId), orderId, at);
    }
}
