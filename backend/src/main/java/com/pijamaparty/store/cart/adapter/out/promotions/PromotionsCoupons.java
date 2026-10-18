package com.pijamaparty.store.cart.adapter.out.promotions;

import com.pijamaparty.store.cart.application.CartDiscount;
import com.pijamaparty.store.cart.application.CouponCheck;
import com.pijamaparty.store.cart.application.Coupons;
import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CartDiscounts;
import com.pijamaparty.store.promotions.api.CouponDiscount;
import com.pijamaparty.store.promotions.api.CouponDiscounts;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Coupons and a cart's discount through the public API of promotions, in the caller's transaction. */
@Component
class PromotionsCoupons implements Coupons {

    private final CouponDiscounts discounts;
    private final CartDiscounts cartDiscounts;

    PromotionsCoupons(CouponDiscounts discounts, CartDiscounts cartDiscounts) {
        this.discounts = discounts;
        this.cartDiscounts = cartDiscounts;
    }

    @Override
    public Optional<CouponCheck> byCode(String code, Money subtotal) {
        return discounts.byCode(code, subtotal).map(PromotionsCoupons::check);
    }

    @Override
    public CartDiscount discount(List<Line> lines, Optional<CouponId> coupon) {
        var discount = cartDiscounts.price(
            lines.stream().map(line -> new CartDiscounts.Line(line.productId(), line.categoryId(), line.amount()))
                .toList(),
            coupon.map(CouponId::value));
        return new CartDiscount(discount.coupon().map(PromotionsCoupons::check), discount.applied().map(applied ->
            new CartDiscount.Applied(CouponId.of(applied.couponId()), applied.code(), applied.automatic(),
                applied.amount())));
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
