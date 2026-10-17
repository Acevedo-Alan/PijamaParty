package com.pijamaparty.store.promotions.domain;

import java.util.Objects;

/**
 * UC-23: an automatic coupon with a scope discounts only the matching lines, so the rules after it and the discount
 * see their subtotal. It always passes: without a matching line that subtotal is zero.
 */
record ScopeRule(CouponScope scope) implements DiscountRule {

    ScopeRule {
        Objects.requireNonNull(scope, "scope");
    }

    @Override
    public Outcome check(DiscountContext context) {
        return new Outcome.Passed(context.narrowedTo(scope::matches));
    }
}
