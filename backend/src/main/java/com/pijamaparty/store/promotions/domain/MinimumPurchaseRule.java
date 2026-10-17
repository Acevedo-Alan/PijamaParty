package com.pijamaparty.store.promotions.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/** UC-10, UC-23: the subtotal, or the matching one for a scoped coupon, reaches the minimum purchase. */
record MinimumPurchaseRule(Money minimum) implements DiscountRule {

    MinimumPurchaseRule {
        Objects.requireNonNull(minimum, "minimum");
    }

    @Override
    public Outcome check(DiscountContext context) {
        return context.subtotal().isLessThan(minimum)
            ? new Outcome.Failed(new Coupon.Evaluation.BelowMinimum(minimum.minus(context.subtotal())))
            : new Outcome.Passed(context);
    }
}
