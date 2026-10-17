package com.pijamaparty.store.promotions.domain;

import java.util.Objects;

/**
 * UC-23: one condition of a coupon. A coupon applies when all its rules pass, checked in order; a new kind of rule is
 * a new implementation.
 */
public interface DiscountRule {

    Outcome check(DiscountContext context);

    sealed interface Outcome {

        /** The rule holds; the next rules and the discount use {@code context}, which a rule may narrow. */
        record Passed(DiscountContext context) implements Outcome {

            public Passed {
                Objects.requireNonNull(context, "context");
            }
        }

        /** The rule does not hold, and {@code reason} says why the coupon does not apply. */
        record Failed(Coupon.Evaluation reason) implements Outcome {

            public Failed {
                Objects.requireNonNull(reason, "reason");
                if (reason instanceof Coupon.Evaluation.Applicable) {
                    throw new IllegalArgumentException("a failed rule is a reason not to apply");
                }
            }
        }
    }
}
