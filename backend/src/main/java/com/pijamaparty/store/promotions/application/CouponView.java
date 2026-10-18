package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponScope;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A coupon as the back office shows it (UC-11, UC-23), with how many confirmed orders used it.
 *
 * @param discountValue the percent, or the cents of a fixed amount
 * @param scope {@link CouponScope#WHOLE_CART} for a code coupon and for an automatic one without a scope
 */
public record CouponView(CouponId id, String code, CouponType type, long discountValue,
                         Optional<LocalDate> validFrom, Optional<LocalDate> validUntil, Optional<Integer> maxUses,
                         Optional<Money> minimumPurchase, boolean automatic, CouponScope scope, boolean active,
                         long uses) {

    public static CouponView of(Coupon coupon, long uses) {
        return new CouponView(coupon.id(), coupon.code().value(), coupon.type(), coupon.discountValue(),
            coupon.validFrom(), coupon.validUntil(), coupon.maxUses(), coupon.minimumPurchase(), coupon.automatic(),
            coupon.scope(), coupon.active(), uses);
    }
}
