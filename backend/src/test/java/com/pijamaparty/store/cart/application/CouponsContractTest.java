package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link Coupons} (UC-10). */
public abstract class CouponsContractTest<T extends Coupons> extends ContractTest<T> {

    /** An active coupon of {@code percent} percent with no validity limits, for a subtotal of at least minimum. */
    protected abstract CouponId givenPercentageCoupon(String code, int percent, Money minimum);

    /** A percentage coupon whose last valid day has passed. */
    protected abstract CouponId givenExpiredCoupon(String code);

    @Test
    void givenAValid10PercentCoupon_whenAskedByItsTypedCodeOrId_thenItAppliesWithA2000Discount() {
        CouponId id = givenPercentageCoupon("VERANO10", 10, Money.of(1));
        CouponCheck applicable = new CouponCheck(id, "VERANO10", CouponCheck.Status.APPLICABLE, Money.of(200_000),
            Money.zero());

        assertThat(subject.byCode(" verano10  ", Money.of(2_000_000))).contains(applicable);
        assertThat(subject.byId(id, Money.of(2_000_000))).contains(applicable);
    }

    @Test
    void givenAPercentageThatIsNotAWholeCent_whenAskedForIt_thenTheDiscountIsRoundedDown() {
        CouponId id = givenPercentageCoupon("PIJAMA15", 15, Money.of(1));

        assertThat(subject.byId(id, Money.of(33_333))).map(CouponCheck::discount).contains(Money.of(4_999));
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

        assertThat(subject.byCode("vencido", Money.of(2_000_000))).contains(new CouponCheck(id, "VENCIDO",
            CouponCheck.Status.EXPIRED, Money.zero(), Money.zero()));
    }

    @Test
    void givenNoSuchCoupon_whenAskedByCodeOrId_thenThereIsNone() {
        givenPercentageCoupon("VERANO10", 10, Money.of(1));

        assertThat(subject.byCode("INVIERNO10", Money.of(2_000_000))).isEmpty();
        assertThat(subject.byId(CouponId.of(UUID.randomUUID()), Money.of(2_000_000))).isEmpty();
    }
}
