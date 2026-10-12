package com.pijamaparty.store.promotions.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link LoadCoupon} and {@link CouponUses} over the same storage (UC-10). */
public abstract class CouponStoreContractTest<T extends LoadCoupon & CouponUses> extends ContractTest<T> {

    /** A stored coupon. */
    protected abstract void givenCoupon(Coupon coupon);

    /** {@code orders} confirmed orders used the coupon (E3). */
    protected abstract void givenRedemptions(CouponId id, int orders);

    @Test
    void givenACouponWithEveryField_whenLoadingItByCodeOrId_thenItHasTheSameState() {
        Coupon coupon = Coupon.of(CouponId.of(UUID.randomUUID()), new CouponCode("VERANO-10"),
            CouponType.FIXED_AMOUNT, 500_000, Optional.of(LocalDate.of(2026, 10, 1)),
            Optional.of(LocalDate.of(2026, 10, 31)), Optional.of(50), Optional.of(Money.of(2_000_000)), false);
        givenCoupon(coupon);
        persistAndForget();

        assertSameState(subject.findByCode(new CouponCode("VERANO-10")), coupon);
        assertSameState(subject.findById(coupon.id()), coupon);
    }

    @Test
    void givenACouponWithoutTheOptionalFields_whenLoadingIt_thenTheyAreStillEmpty() {
        Coupon coupon = Coupon.of(CouponId.of(UUID.randomUUID()), new CouponCode("PIJAMA15"), CouponType.PERCENTAGE,
            15, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true);
        givenCoupon(coupon);
        persistAndForget();

        assertSameState(subject.findById(coupon.id()), coupon);
    }

    @Test
    void givenNoCouponWithTheCodeOrId_whenLoadingIt_thenNothingIsFound() {
        givenCoupon(Coupon.of(CouponId.of(UUID.randomUUID()), new CouponCode("PIJAMA15"), CouponType.PERCENTAGE,
            15, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true));
        persistAndForget();

        assertThat(subject.findByCode(new CouponCode("PIJAMA16"))).isEmpty();
        assertThat(subject.findById(CouponId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void givenACouponUsedByOrders_whenCountingItsUses_thenEachOrderCountsOnce() {
        CouponId used = CouponId.of(UUID.randomUUID());
        CouponId unused = CouponId.of(UUID.randomUUID());
        givenCoupon(Coupon.of(used, new CouponCode("USED"), CouponType.PERCENTAGE, 10, Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), true));
        givenCoupon(Coupon.of(unused, new CouponCode("UNUSED"), CouponType.PERCENTAGE, 10, Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), true));
        givenRedemptions(used, 3);
        persistAndForget();

        assertThat(subject.count(used)).isEqualTo(3);
        assertThat(subject.count(unused)).isZero();
    }

    private static void assertSameState(Optional<Coupon> loaded, Coupon expected) {
        assertThat(loaded).hasValueSatisfying(
            coupon -> assertThat(coupon).usingRecursiveComparison().isEqualTo(expected));
    }
}
