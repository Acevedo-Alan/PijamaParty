package com.pijamaparty.store.promotions.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** UC-10, UC-23: today is within the coupon's validity, both days inclusive; either end may be open. */
record ValidityRule(Optional<LocalDate> validFrom, Optional<LocalDate> validUntil) implements DiscountRule {

    ValidityRule {
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(validUntil, "validUntil");
    }

    @Override
    public Outcome check(DiscountContext context) {
        if (validFrom.filter(context.today()::isBefore).isPresent()) {
            return new Outcome.Failed(new Coupon.Evaluation.NotStarted());
        }
        if (validUntil.filter(context.today()::isAfter).isPresent()) {
            return new Outcome.Failed(new Coupon.Evaluation.Expired());
        }
        return new Outcome.Passed(context);
    }
}
