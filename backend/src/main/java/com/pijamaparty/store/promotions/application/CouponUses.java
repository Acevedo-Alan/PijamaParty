package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.domain.CouponId;

/** How many confirmed orders used a coupon (UC-10, UC-11): its redemptions, recorded in E3. */
public interface CouponUses {

    long count(CouponId id);
}
