package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CouponDiscount;
import com.pijamaparty.store.promotions.api.CouponStatus;
import com.pijamaparty.store.promotions.domain.Coupon;

/** UC-10: a coupon's evaluation as the public API answers it. */
final class CouponDiscountOf {

    private CouponDiscountOf() {
    }

    static CouponDiscount of(Coupon coupon, Coupon.Evaluation evaluation) {
        Money zero = Money.zero();
        return switch (evaluation) {
            case Coupon.Evaluation.Applicable applicable ->
                discount(coupon, CouponStatus.APPLICABLE, applicable.discount(), zero);
            case Coupon.Evaluation.Inactive inactive -> discount(coupon, CouponStatus.INACTIVE, zero, zero);
            case Coupon.Evaluation.NotStarted notStarted -> discount(coupon, CouponStatus.NOT_STARTED, zero, zero);
            case Coupon.Evaluation.Expired expired -> discount(coupon, CouponStatus.EXPIRED, zero, zero);
            case Coupon.Evaluation.UsedUp usedUp -> discount(coupon, CouponStatus.USED_UP, zero, zero);
            case Coupon.Evaluation.BelowMinimum below ->
                discount(coupon, CouponStatus.BELOW_MINIMUM, zero, below.missing());
        };
    }

    private static CouponDiscount discount(Coupon coupon, CouponStatus status, Money discount, Money missing) {
        return new CouponDiscount(coupon.id().value(), coupon.code().value(), status, discount, missing);
    }
}
