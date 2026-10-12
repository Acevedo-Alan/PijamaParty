package com.pijamaparty.store.promotions.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadCoupon}, {@link CouponUses}, {@link CouponSummaries}, {@link SaveCoupon} and
 * {@link RemoveStoredCoupon} over the same storage (UC-10, UC-11).
 */
public abstract class CouponStoreContractTest<
    T extends LoadCoupon & CouponUses & CouponSummaries & SaveCoupon & RemoveStoredCoupon>
    extends ContractTest<T> {

    /** Whole seconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    private static final Instant CREATED = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-10T12:00:00Z");

    /** {@code orders} confirmed orders used the coupon (E3). */
    protected abstract void givenRedemptions(CouponId id, int orders);

    @Test
    void givenACouponWithEveryField_whenLoadingItByCodeOrId_thenItHasTheSameState() {
        Coupon coupon = Coupon.of(CouponId.of(UUID.randomUUID()), new CouponCode("VERANO-10"),
            CouponType.FIXED_AMOUNT, 500_000, Optional.of(LocalDate.of(2026, 10, 1)),
            Optional.of(LocalDate.of(2026, 10, 31)), Optional.of(50), Optional.of(Money.of(2_000_000)), false);
        assertThat(subject.add(coupon, CREATED)).isEqualTo(SaveCoupon.Outcome.SAVED);
        persistAndForget();

        assertSameState(subject.findByCode(new CouponCode("VERANO-10")), coupon);
        assertSameState(subject.findById(coupon.id()), coupon);
    }

    @Test
    void givenACouponWithoutTheOptionalFields_whenLoadingIt_thenTheyAreStillEmpty() {
        Coupon coupon = percentage("PIJAMA15");
        subject.add(coupon, CREATED);
        persistAndForget();

        assertSameState(subject.findById(coupon.id()), coupon);
    }

    @Test
    void givenNoCouponWithTheCodeOrId_whenLoadingIt_thenNothingIsFound() {
        subject.add(percentage("PIJAMA15"), CREATED);
        persistAndForget();

        assertThat(subject.findByCode(new CouponCode("PIJAMA16"))).isEmpty();
        assertThat(subject.findById(CouponId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void givenACouponUsedByOrders_whenCountingItsUses_thenEachOrderCountsOnce() {
        Coupon used = percentage("USED");
        Coupon unused = percentage("UNUSED");
        subject.add(used, CREATED);
        subject.add(unused, CREATED);
        givenRedemptions(used.id(), 3);
        persistAndForget();

        assertThat(subject.count(used.id())).isEqualTo(3);
        assertThat(subject.count(unused.id())).isZero();
    }

    @Test
    void givenAStoredCoupon_whenEveryFieldChangesAndItIsSavedAgain_thenTheChangesAreKept() {
        Coupon coupon = percentage("VERANO10");
        subject.add(coupon, CREATED);
        persistAndForget();

        Coupon changed = Coupon.of(coupon.id(), new CouponCode("INVIERNO"), CouponType.FIXED_AMOUNT, 300_000,
            Optional.of(LocalDate.of(2026, 6, 1)), Optional.of(LocalDate.of(2026, 8, 31)), Optional.of(10),
            Optional.of(Money.of(1_500_000)), false);
        assertThat(subject.update(changed, LATER)).isEqualTo(SaveCoupon.Outcome.SAVED);
        persistAndForget();

        assertSameState(subject.findById(coupon.id()), changed);
        assertThat(subject.findByCode(new CouponCode("VERANO10"))).isEmpty();
    }

    @Test
    void givenACodeInUse_whenAnotherCouponIsSavedWithIt_thenItIsNotSavedAndTheStoreStaysUsable() {
        Coupon existing = percentage("VERANO10");
        Coupon other = percentage("INVIERNO");
        subject.add(existing, CREATED);

        assertThat(subject.add(percentage("VERANO10"), CREATED)).isEqualTo(SaveCoupon.Outcome.CODE_ALREADY_USED);
        assertThat(subject.add(other, CREATED)).isEqualTo(SaveCoupon.Outcome.SAVED);
        Coupon renamed = Coupon.of(other.id(), new CouponCode("VERANO10"), CouponType.PERCENTAGE, 10,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true);
        assertThat(subject.update(renamed, LATER)).isEqualTo(SaveCoupon.Outcome.CODE_ALREADY_USED);
        persistAndForget();

        assertSameState(subject.findByCode(new CouponCode("VERANO10")), existing);
        assertSameState(subject.findById(other.id()), other);
    }

    @Test
    void givenAMissingCoupon_whenUpdatingIt_thenItIsNotBroughtBack() {
        Coupon never = percentage("NUNCA");

        assertThat(subject.update(never, LATER)).isEqualTo(SaveCoupon.Outcome.NOT_FOUND);
        persistAndForget();

        assertThat(subject.findById(never.id())).isEmpty();
    }

    @Test
    void givenSeveralCoupons_whenListing_thenAllComeNewestFirstWithTheirUses() {
        Coupon oldest = percentage("PRIMERO");
        Coupon middle = percentage("SEGUNDO").deactivated();
        Coupon newest = percentage("TERCERO");
        subject.add(middle, CREATED.plusSeconds(60));
        subject.add(newest, CREATED.plusSeconds(120));
        subject.add(oldest, CREATED);
        givenRedemptions(middle.id(), 2);
        // An update keeps the creation order.
        subject.update(oldest.deactivated(), LATER);
        persistAndForget();

        assertThat(subject.listNewestFirst()).containsExactly(
            CouponView.of(newest, 0), CouponView.of(middle, 2), CouponView.of(oldest.deactivated(), 0));
    }

    @Test
    void givenCouponsWithAndWithoutUses_whenRemovingThem_thenOnlyTheUnusedOneGoes() {
        Coupon used = percentage("USED");
        Coupon unused = percentage("UNUSED");
        subject.add(used, CREATED);
        subject.add(unused, CREATED);
        givenRedemptions(used.id(), 1);

        assertThat(subject.remove(used.id())).isEqualTo(RemoveStoredCoupon.Outcome.HAS_USES);
        assertThat(subject.remove(unused.id())).isEqualTo(RemoveStoredCoupon.Outcome.REMOVED);
        assertThat(subject.remove(unused.id())).isEqualTo(RemoveStoredCoupon.Outcome.NOT_FOUND);
        persistAndForget();

        assertSameState(subject.findById(used.id()), used);
        assertThat(subject.findById(unused.id())).isEmpty();
    }

    private static Coupon percentage(String code) {
        return Coupon.of(CouponId.of(UUID.randomUUID()), new CouponCode(code), CouponType.PERCENTAGE, 15,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true);
    }

    private static void assertSameState(Optional<Coupon> loaded, Coupon expected) {
        assertThat(loaded).hasValueSatisfying(
            coupon -> assertThat(coupon).usingRecursiveComparison().isEqualTo(expected));
    }
}
