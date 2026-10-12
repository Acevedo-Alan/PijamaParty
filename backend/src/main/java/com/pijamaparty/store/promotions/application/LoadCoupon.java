package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import java.util.Optional;

/** Outbound port: reads a coupon within the caller's transaction (UC-10, UC-11). */
public interface LoadCoupon {

    Optional<Coupon> findByCode(CouponCode code);

    Optional<Coupon> findById(CouponId id);
}
