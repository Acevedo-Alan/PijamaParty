package com.pijamaparty.store.promotions.domain;

/** UC-10, UC-23: the coupon has uses left; they are counted when an order is paid (UC-15). */
record MaxUsesRule(int maxUses) implements DiscountRule {

    @Override
    public Outcome check(DiscountContext context) {
        return context.uses() >= maxUses
            ? new Outcome.Failed(new Coupon.Evaluation.UsedUp())
            : new Outcome.Passed(context);
    }
}
