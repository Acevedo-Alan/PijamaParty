package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.api.CartDiscount;
import com.pijamaparty.store.promotions.api.CartDiscounts;
import com.pijamaparty.store.promotions.domain.AppliedDiscount;
import com.pijamaparty.store.promotions.domain.CountedCoupon;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.DiscountEngine;
import com.pijamaparty.store.promotions.domain.DiscountLine;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-10, UC-23: prices a cart's discount with the {@link DiscountEngine}. The clock is in the store's time zone, so its
 * date is the store date.
 */
@Service
class CartDiscountsService implements CartDiscounts {

    private final LoadCoupon loadCoupon;
    private final CouponUses uses;
    private final LoadAutomaticCoupons automaticCoupons;
    private final Clock clock;

    CartDiscountsService(LoadCoupon loadCoupon, CouponUses uses, LoadAutomaticCoupons automaticCoupons, Clock clock) {
        this.loadCoupon = loadCoupon;
        this.uses = uses;
        this.automaticCoupons = automaticCoupons;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CartDiscount price(List<Line> lines, Optional<UUID> couponId) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(couponId, "couponId");
        // A cart holds only code coupons: CouponDiscounts.byCode does not know an automatic one.
        Optional<CountedCoupon> held = couponId.map(CouponId::of)
            .flatMap(loadCoupon::findById)
            .filter(coupon -> !coupon.automatic())
            .map(coupon -> new CountedCoupon(coupon, uses.count(coupon.id())));
        DiscountEngine.Pricing pricing = DiscountEngine.price(
            lines.stream().map(line -> new DiscountLine(line.productId(), line.categoryId(), line.amount())).toList(),
            LocalDate.now(clock), held, automaticCoupons.activeOldestFirst());
        return new CartDiscount(
            held.map(counted -> CouponDiscountOf.of(counted.coupon(), pricing.codeCoupon().orElseThrow())),
            pricing.applied().map(CartDiscountsService::applied));
    }

    private static CartDiscount.Applied applied(AppliedDiscount discount) {
        return new CartDiscount.Applied(discount.coupon().id().value(), discount.coupon().code().value(),
            discount.coupon().automatic(), discount.amount());
    }
}
