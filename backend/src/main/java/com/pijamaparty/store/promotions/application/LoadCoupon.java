package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import java.util.Optional;

/** Reads a coupon (UC-10). */
public interface LoadCoupon {

    Optional<Coupon> findByCode(CouponCode code);

    Optional<Coupon> findById(CouponId id);
}
