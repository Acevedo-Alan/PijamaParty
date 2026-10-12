package com.pijamaparty.store.cart.adapter.out.promotions;

import com.pijamaparty.store.cart.application.CouponCheck;
import com.pijamaparty.store.cart.application.Coupons;
import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CouponDiscount;
import com.pijamaparty.store.promotions.api.CouponDiscounts;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Coupons through the public API of promotions, in the caller's transaction. */
@Component
class PromotionsCoupons implements Coupons {

    private final CouponDiscounts discounts;

    PromotionsCoupons(CouponDiscounts discounts) {
        this.discounts = discounts;
    }

    @Override
    public Optional<CouponCheck> byCode(String code, Money subtotal) {
        return discounts.byCode(code, subtotal).map(PromotionsCoupons::check);
    }

    @Override
    public Optional<CouponCheck> byId(CouponId id, Money subtotal) {
        return discounts.byId(id.value(), subtotal).map(PromotionsCoupons::check);
    }

    private static CouponCheck check(CouponDiscount discount) {
        CouponCheck.Status status = switch (discount.status()) {
            case APPLICABLE -> CouponCheck.Status.APPLICABLE;
            case INACTIVE -> CouponCheck.Status.INACTIVE;
            case NOT_STARTED -> CouponCheck.Status.NOT_STARTED;
            case EXPIRED -> CouponCheck.Status.EXPIRED;
            case USED_UP -> CouponCheck.Status.USED_UP;
            case BELOW_MINIMUM -> CouponCheck.Status.BELOW_MINIMUM;
        };
        return new CouponCheck(CouponId.of(discount.couponId()), discount.code(), status, discount.discount(),
            discount.missing());
    }
}
