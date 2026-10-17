package com.pijamaparty.store.promotions.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-23: prices a cart's discount. It evaluates the cart's code coupon (UC-10) and every automatic coupon, and only
 * the largest discount applies: discounts never stack. On a tie the code coupon wins, and between automatic coupons
 * the oldest one. An automatic coupon that would take nothing off does not apply.
 */
public final class DiscountEngine {

    private DiscountEngine() {
    }

    /**
     * @param lines the cart's {@code AVAILABLE} lines (UC-09)
     * @param today the store date (R-07)
     * @param codeCoupon the code coupon the cart holds, if any
     * @param automaticCoupons the automatic coupons, oldest first ({@code created_at}, then {@code id})
     */
    public static Pricing price(List<DiscountLine> lines, LocalDate today, Optional<CountedCoupon> codeCoupon,
                                List<CountedCoupon> automaticCoupons) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(codeCoupon, "codeCoupon");
        Objects.requireNonNull(automaticCoupons, "automaticCoupons");
        if (codeCoupon.filter(counted -> counted.coupon().automatic()).isPresent()) {
            throw new IllegalArgumentException("an automatic coupon cannot be held as a code coupon");
        }
        if (automaticCoupons.stream().anyMatch(counted -> !counted.coupon().automatic())) {
            throw new IllegalArgumentException("only automatic coupons apply on their own");
        }

        Optional<Coupon.Evaluation> codeEvaluation = codeCoupon.map(counted -> evaluate(counted, lines, today));
        Optional<AppliedDiscount> best = codeCoupon.flatMap(counted -> applied(counted, codeEvaluation.orElseThrow()));
        for (CountedCoupon automatic : automaticCoupons) {
            Optional<AppliedDiscount> candidate = applied(automatic, evaluate(automatic, lines, today))
                .filter(discount -> !discount.amount().isZero());
            if (candidate.isPresent() && isLarger(candidate.get(), best)) {
                best = candidate;
            }
        }
        return new Pricing(codeEvaluation, best);
    }

    private static Coupon.Evaluation evaluate(CountedCoupon counted, List<DiscountLine> lines, LocalDate today) {
        return counted.coupon().evaluate(DiscountContext.ofLines(lines, today, counted.uses()));
    }

    private static Optional<AppliedDiscount> applied(CountedCoupon counted, Coupon.Evaluation evaluation) {
        return evaluation instanceof Coupon.Evaluation.Applicable(Money discount)
            ? Optional.of(new AppliedDiscount(counted.coupon(), discount))
            : Optional.empty();
    }

    /** Strictly larger, so on a tie the coupon already chosen stays: the code coupon, or the older automatic one. */
    private static boolean isLarger(AppliedDiscount candidate, Optional<AppliedDiscount> best) {
        return best.map(current -> candidate.amount().isGreaterThan(current.amount())).orElse(true);
    }

    /**
     * @param codeCoupon the evaluation of the cart's code coupon, as UC-10 shows it; empty when it holds none
     * @param applied the coupon that discounts, if any
     */
    public record Pricing(Optional<Coupon.Evaluation> codeCoupon, Optional<AppliedDiscount> applied) {

        public Pricing {
            Objects.requireNonNull(codeCoupon, "codeCoupon");
            Objects.requireNonNull(applied, "applied");
        }
    }
}
