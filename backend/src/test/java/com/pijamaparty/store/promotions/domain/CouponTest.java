package com.pijamaparty.store.promotions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.domain.Coupon.Evaluation;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-10: when a coupon applies and how much it discounts; UC-11: the rules of its data. */
class CouponTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final Money SUBTOTAL = Money.of(2_000_000);

    @Test
    void givenAValid10PercentCoupon_whenAppliedToA20000Cart_thenTheDiscountIs2000() {
        assertThat(percentage(10).evaluate(SUBTOTAL, TODAY, 0)).isEqualTo(new Evaluation.Applicable(Money.of(200_000)));
    }

    @Test
    void givenAPercentageThatIsNotAWholeCent_whenEvaluated_thenTheDiscountIsRoundedDown() {
        // 15% of ARS 333.33 is 4999.95 cents.
        assertThat(percentage(15).evaluate(Money.of(33_333), TODAY, 0))
            .isEqualTo(new Evaluation.Applicable(Money.of(4_999)));
    }

    @Test
    void givenA100PercentCoupon_whenEvaluated_thenItDiscountsTheWholeSubtotal() {
        assertThat(percentage(100).evaluate(SUBTOTAL, TODAY, 0)).isEqualTo(new Evaluation.Applicable(SUBTOTAL));
    }

    @Test
    void givenAFixedAmountCoupon_whenEvaluated_thenItDiscountsItsValueButNeverMoreThanTheSubtotal() {
        Coupon fiveThousand = coupon(CouponType.FIXED_AMOUNT, 500_000, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), true);

        assertThat(fiveThousand.evaluate(SUBTOTAL, TODAY, 0)).isEqualTo(new Evaluation.Applicable(Money.of(500_000)));
        assertThat(fiveThousand.evaluate(Money.of(300_000), TODAY, 0))
            .isEqualTo(new Evaluation.Applicable(Money.of(300_000)));
    }

    @Test
    void givenAnInactiveCoupon_whenEvaluated_thenItIsInactive() {
        Coupon inactive = coupon(CouponType.PERCENTAGE, 10, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), false);

        assertThat(inactive.evaluate(SUBTOTAL, TODAY, 0)).isEqualTo(new Evaluation.Inactive());
    }

    @Test
    void givenAValidityPeriod_whenEvaluatedAroundIt_thenBothBoundsAreInclusive() {
        Coupon october = coupon(CouponType.PERCENTAGE, 10, Optional.of(LocalDate.of(2026, 10, 1)),
            Optional.of(LocalDate.of(2026, 10, 31)), Optional.empty(), Optional.empty(), true);

        assertThat(october.evaluate(SUBTOTAL, LocalDate.of(2026, 9, 30), 0)).isEqualTo(new Evaluation.NotStarted());
        assertThat(october.evaluate(SUBTOTAL, LocalDate.of(2026, 10, 1), 0)).isInstanceOf(Evaluation.Applicable.class);
        assertThat(october.evaluate(SUBTOTAL, LocalDate.of(2026, 10, 31), 0))
            .isInstanceOf(Evaluation.Applicable.class);
        assertThat(october.evaluate(SUBTOTAL, LocalDate.of(2026, 11, 1), 0)).isEqualTo(new Evaluation.Expired());
    }

    @Test
    void givenAnOpenEndedValidity_whenEvaluatedFarFromIt_thenItApplies() {
        Coupon fromOctober = coupon(CouponType.PERCENTAGE, 10, Optional.of(LocalDate.of(2026, 10, 1)),
            Optional.empty(), Optional.empty(), Optional.empty(), true);
        Coupon untilOctober = coupon(CouponType.PERCENTAGE, 10, Optional.empty(),
            Optional.of(LocalDate.of(2026, 10, 31)), Optional.empty(), Optional.empty(), true);

        assertThat(fromOctober.evaluate(SUBTOTAL, LocalDate.of(2030, 1, 1), 0))
            .isInstanceOf(Evaluation.Applicable.class);
        assertThat(untilOctober.evaluate(SUBTOTAL, LocalDate.of(2020, 1, 1), 0))
            .isInstanceOf(Evaluation.Applicable.class);
    }

    @Test
    void givenAUsedUpCoupon_whenEvaluated_thenItIsRejected() {
        Coupon threeUses = coupon(CouponType.PERCENTAGE, 10, Optional.empty(), Optional.empty(), Optional.of(3),
            Optional.empty(), true);

        assertThat(threeUses.evaluate(SUBTOTAL, TODAY, 2)).isInstanceOf(Evaluation.Applicable.class);
        assertThat(threeUses.evaluate(SUBTOTAL, TODAY, 3)).isEqualTo(new Evaluation.UsedUp());
    }

    @Test
    void givenACartBelowTheMinimum_whenEvaluated_thenItTellsHowMuchIsMissing() {
        Coupon minimum = coupon(CouponType.PERCENTAGE, 10, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(Money.of(2_500_000)), true);

        assertThat(minimum.evaluate(SUBTOTAL, TODAY, 0)).isEqualTo(new Evaluation.BelowMinimum(Money.of(500_000)));
        assertThat(minimum.evaluate(Money.of(2_500_000), TODAY, 0))
            .isEqualTo(new Evaluation.Applicable(Money.of(250_000)));
    }

    @Test
    void givenSeveralFailures_whenEvaluated_thenTheFirstCheckInOrderIsTheAnswer() {
        Coupon everythingWrong = coupon(CouponType.PERCENTAGE, 10, Optional.empty(),
            Optional.of(TODAY.minusDays(1)), Optional.of(1), Optional.of(Money.of(9_000_000)), false);
        Coupon expiredAndUsedUp = coupon(CouponType.PERCENTAGE, 10, Optional.empty(),
            Optional.of(TODAY.minusDays(1)), Optional.of(1), Optional.of(Money.of(9_000_000)), true);
        Coupon usedUpAndBelowMinimum = coupon(CouponType.PERCENTAGE, 10, Optional.empty(), Optional.empty(),
            Optional.of(1), Optional.of(Money.of(9_000_000)), true);

        assertThat(everythingWrong.evaluate(SUBTOTAL, TODAY, 1)).isEqualTo(new Evaluation.Inactive());
        assertThat(expiredAndUsedUp.evaluate(SUBTOTAL, TODAY, 1)).isEqualTo(new Evaluation.Expired());
        assertThat(usedUpAndBelowMinimum.evaluate(SUBTOTAL, TODAY, 1)).isEqualTo(new Evaluation.UsedUp());
    }

    @Test
    void givenDataAgainstTheRulesOfUC11_whenBuildingACoupon_thenItIsRefused() {
        assertThatThrownBy(() -> percentage(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> percentage(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coupon(CouponType.FIXED_AMOUNT, 0, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coupon(CouponType.PERCENTAGE, 10, Optional.of(TODAY), Optional.of(TODAY.minusDays(1)),
            Optional.empty(), Optional.empty(), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coupon(CouponType.PERCENTAGE, 10, Optional.empty(), Optional.empty(), Optional.of(0),
            Optional.empty(), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coupon(CouponType.PERCENTAGE, 10, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.of(Money.zero()), true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenATypedCode_whenParsed_thenSpacesAndCaseAreIgnoredAndOnlyTheUC11FormatIsACode() {
        assertThat(CouponCode.parse("  verano-10 ")).contains(new CouponCode("VERANO-10"));
        assertThat(CouponCode.parse("AB")).isEmpty();
        assertThat(CouponCode.parse("A".repeat(21))).isEmpty();
        assertThat(CouponCode.parse("VERANO 10")).isEmpty();
        assertThat(CouponCode.parse("VERAÑO")).isEmpty();
        assertThat(CouponCode.parse(null)).isEmpty();
    }

    private static Coupon percentage(int percent) {
        return coupon(CouponType.PERCENTAGE, percent, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), true);
    }

    private static Coupon coupon(CouponType type, long value, Optional<LocalDate> validFrom,
                                 Optional<LocalDate> validUntil, Optional<Integer> maxUses,
                                 Optional<Money> minimumPurchase, boolean active) {
        return Coupon.of(CouponId.of(UUID.randomUUID()), new CouponCode("VERANO10"), type, value, validFrom,
            validUntil, maxUses, minimumPurchase, active);
    }
}
