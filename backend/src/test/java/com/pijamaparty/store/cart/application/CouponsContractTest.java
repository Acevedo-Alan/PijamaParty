package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link Coupons} (UC-10, UC-23). */
public abstract class CouponsContractTest<T extends Coupons> extends ContractTest<T> {

    private static final UUID PAJAMAS = UUID.randomUUID();
    private static final UUID ROBES = UUID.randomUUID();

    /** An active code coupon of {@code percent} percent with no validity limits, for a subtotal of at least minimum. */
    protected abstract CouponId givenPercentageCoupon(String code, int percent, Money minimum);

    /** A percentage code coupon whose last valid day has passed. */
    protected abstract CouponId givenExpiredCoupon(String code);

    /**
     * UC-23: an active automatic coupon of {@code percent} percent with no validity limits, for a matching subtotal of
     * at least {@code minimum}: of the lines of {@code category}, or of the whole cart without one. Each one is newer
     * than those given before it.
     */
    protected abstract CouponId givenAutomaticCoupon(String code, int percent, Money minimum, Optional<UUID> category);

    @Test
    void givenAValid10PercentCoupon_whenAskedByItsTypedCodeOrHeldByTheCart_thenItAppliesWithA2000Discount() {
        CouponId id = givenPercentageCoupon("VERANO10", 10, Money.of(1));
        CouponCheck applicable = new CouponCheck(id, "VERANO10", CouponCheck.Status.APPLICABLE, Money.of(200_000),
            Money.zero());

        assertThat(subject.byCode(" verano10  ", Money.of(2_000_000))).contains(applicable);
        assertThat(subject.discount(List.of(line(PAJAMAS, 2_000_000)), Optional.of(id))).isEqualTo(new CartDiscount(
            Optional.of(applicable),
            Optional.of(new CartDiscount.Applied(id, "VERANO10", false, Money.of(200_000)))));
    }

    @Test
    void givenAPercentageThatIsNotAWholeCent_whenPricingTheCart_thenTheDiscountIsRoundedDown() {
        CouponId id = givenPercentageCoupon("PIJAMA15", 15, Money.of(1));

        assertThat(subject.discount(List.of(line(PAJAMAS, 33_333)), Optional.of(id)).amount())
            .isEqualTo(Money.of(4_999));
    }

    @Test
    void givenACartBelowTheMinimum_whenAskedForTheCoupon_thenItTellsHowMuchIsMissing() {
        CouponId id = givenPercentageCoupon("MINIMO", 10, Money.of(3_000_000));

        assertThat(subject.byCode("MINIMO", Money.of(2_000_000))).contains(new CouponCheck(id, "MINIMO",
            CouponCheck.Status.BELOW_MINIMUM, Money.zero(), Money.of(1_000_000)));
    }

    @Test
    void givenAnExpiredCoupon_whenAskedForIt_thenItIsExpiredWithoutADiscount() {
        CouponId id = givenExpiredCoupon("VENCIDO");
        CouponCheck expired = new CouponCheck(id, "VENCIDO", CouponCheck.Status.EXPIRED, Money.zero(), Money.zero());

        assertThat(subject.byCode("vencido", Money.of(2_000_000))).contains(expired);
        assertThat(subject.discount(List.of(line(PAJAMAS, 2_000_000)), Optional.of(id)))
            .isEqualTo(new CartDiscount(Optional.of(expired), Optional.empty()));
    }

    @Test
    void givenNoSuchCoupon_whenAskedByCodeOrHeldByTheCart_thenThereIsNone() {
        givenPercentageCoupon("VERANO10", 10, Money.of(1));

        assertThat(subject.byCode("INVIERNO10", Money.of(2_000_000))).isEmpty();
        assertThat(subject.discount(List.of(line(PAJAMAS, 2_000_000)), Optional.of(CouponId.of(UUID.randomUUID()))))
            .isEqualTo(CartDiscount.NONE);
    }

    @Test
    void givenARule10PercentFrom30000_whenTheCartExceeds30000_thenItsDiscountAppliesWithoutACode() {
        CouponId id = givenAutomaticCoupon("VERANO10", 10, Money.of(3_000_000), Optional.empty());

        assertThat(subject.discount(List.of(line(PAJAMAS, 3_500_000)), Optional.empty())).isEqualTo(new CartDiscount(
            Optional.empty(), Optional.of(new CartDiscount.Applied(id, "VERANO10", true, Money.of(350_000)))));
        assertThat(subject.discount(List.of(line(PAJAMAS, 2_500_000)), Optional.empty()))
            .isEqualTo(CartDiscount.NONE);
    }

    @Test
    void givenAnAutomaticCoupon_whenItsCodeIsTyped_thenItIsUnknown() {
        givenAutomaticCoupon("VERANO10", 10, Money.of(1), Optional.empty());

        assertThat(subject.byCode("VERANO10", Money.of(2_000_000))).isEmpty();
    }

    @Test
    void givenAnAutomaticCouponOfACategory_whenTheCartHoldsItsItemsAndOthers_thenOnlyItsItemsAreDiscounted() {
        UUID robes = UUID.randomUUID();
        givenAutomaticCoupon("BATAS20", 20, Money.of(1_000_000), Optional.of(robes));

        assertThat(subject.discount(List.of(line(PAJAMAS, 3_000_000), new Coupons.Line(ROBES, robes,
            Money.of(1_000_000))), Optional.empty()).amount()).isEqualTo(Money.of(200_000));
        assertThat(subject.discount(List.of(line(PAJAMAS, 3_000_000), new Coupons.Line(ROBES, robes,
            Money.of(900_000))), Optional.empty()).applied()).isEmpty();
    }

    @Test
    void givenAHeldCodeAndAnAutomaticCouponWithTheSameDiscount_whenPricingTheCart_thenTheCodeApplies() {
        givenAutomaticCoupon("VERANO10", 10, Money.of(1), Optional.empty());
        CouponId code = givenPercentageCoupon("PIJAMA10", 10, Money.of(1));

        assertThat(subject.discount(List.of(line(PAJAMAS, 2_000_000)), Optional.of(code)).applied())
            .contains(new CartDiscount.Applied(code, "PIJAMA10", false, Money.of(200_000)));
    }

    @Test
    void givenAnAutomaticCouponThatDiscountsMoreThanTheHeldCode_whenPricingTheCart_thenOnlyTheAutomaticOneApplies() {
        CouponId automatic = givenAutomaticCoupon("VERANO20", 20, Money.of(1), Optional.empty());
        CouponId code = givenPercentageCoupon("PIJAMA10", 10, Money.of(1));

        CartDiscount discount = subject.discount(List.of(line(PAJAMAS, 2_000_000)), Optional.of(code));

        assertThat(discount.coupon()).map(CouponCheck::status).contains(CouponCheck.Status.APPLICABLE);
        assertThat(discount.applied()).contains(new CartDiscount.Applied(automatic, "VERANO20", true,
            Money.of(400_000)));
    }

    @Test
    void givenTwoAutomaticCouponsWithTheSameDiscount_whenPricingTheCart_thenTheOldestApplies() {
        CouponId older = givenAutomaticCoupon("VERANO10", 10, Money.of(1), Optional.empty());
        givenAutomaticCoupon("OTONO10", 10, Money.of(1), Optional.empty());

        assertThat(subject.discount(List.of(line(PAJAMAS, 2_000_000)), Optional.empty()).applied())
            .map(CartDiscount.Applied::id).contains(older);
    }

    private static Coupons.Line line(UUID product, long cents) {
        return new Coupons.Line(product, UUID.randomUUID(), Money.of(cents));
    }
}
