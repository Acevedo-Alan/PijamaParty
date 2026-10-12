package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CouponDiscount;
import com.pijamaparty.store.promotions.api.CouponDiscounts;
import com.pijamaparty.store.promotions.api.CouponStatus;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-10: evaluates a coupon for a cart. The clock is in the store's time zone, so its date is the store date. */
@Service
class CouponDiscountsService implements CouponDiscounts {

    private final LoadCoupon loadCoupon;
    private final CouponUses uses;
    private final Clock clock;

    CouponDiscountsService(LoadCoupon loadCoupon, CouponUses uses, Clock clock) {
        this.loadCoupon = loadCoupon;
        this.uses = uses;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CouponDiscount> byCode(String code, Money subtotal) {
        Objects.requireNonNull(subtotal, "subtotal");
        return CouponCode.parse(code).flatMap(loadCoupon::findByCode).map(coupon -> evaluate(coupon, subtotal));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CouponDiscount> byId(UUID couponId, Money subtotal) {
        Objects.requireNonNull(subtotal, "subtotal");
        return loadCoupon.findById(CouponId.of(couponId)).map(coupon -> evaluate(coupon, subtotal));
    }

    private CouponDiscount evaluate(Coupon coupon, Money subtotal) {
        var evaluation = coupon.evaluate(subtotal, LocalDate.now(clock), uses.count(coupon.id()));
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
