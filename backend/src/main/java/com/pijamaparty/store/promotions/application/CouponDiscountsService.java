package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CouponDiscount;
import com.pijamaparty.store.promotions.api.CouponDiscounts;
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

/**
 * UC-10: evaluates a code coupon for a cart; an automatic one is unknown here (UC-23). The clock is in the store's
 * time zone, so its date is the store date.
 */
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
        return CouponCode.parse(code).flatMap(loadCoupon::findByCode)
            .filter(coupon -> !coupon.automatic())
            .map(coupon -> evaluate(coupon, subtotal));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CouponDiscount> byId(UUID couponId, Money subtotal) {
        Objects.requireNonNull(subtotal, "subtotal");
        return loadCoupon.findById(CouponId.of(couponId))
            .filter(coupon -> !coupon.automatic())
            .map(coupon -> evaluate(coupon, subtotal));
    }

    private CouponDiscount evaluate(Coupon coupon, Money subtotal) {
        return CouponDiscountOf.of(coupon, coupon.evaluate(subtotal, LocalDate.now(clock), uses.count(coupon.id())));
    }
}
