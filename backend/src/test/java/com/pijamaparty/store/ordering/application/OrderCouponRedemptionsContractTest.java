package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link OrderCouponRedemptions} (UC-15, UC-10): a paid order uses its coupon once. */
public abstract class OrderCouponRedemptionsContractTest<T extends OrderCouponRedemptions> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-12T15:10:00Z");

    /** A coupon that exists, without uses; answers its id. */
    protected abstract UUID givenCoupon();

    /** How many orders used the coupon. */
    protected abstract long usesOf(UUID couponId);

    @Test
    void givenAPaidOrderWithACoupon_whenItsUseIsRecordedTwice_thenTheCouponHasOneMoreUse() {
        UUID coupon = givenCoupon();
        OrderId order = OrderId.of(UUID.randomUUID());

        subject.redeem(coupon, order, NOW);
        subject.redeem(coupon, order, NOW.plusSeconds(60));

        assertThat(usesOf(coupon)).isOne();
    }

    @Test
    void givenTwoPaidOrdersWithTheSameCoupon_whenTheirUsesAreRecorded_thenEachCounts() {
        UUID coupon = givenCoupon();

        subject.redeem(coupon, OrderId.of(UUID.randomUUID()), NOW);
        subject.redeem(coupon, OrderId.of(UUID.randomUUID()), NOW);

        assertThat(usesOf(coupon)).isEqualTo(2);
    }

    @Test
    void givenACouponDeletedSinceTheCheckout_whenItsUseIsRecorded_thenNothingFails() {
        UUID deleted = UUID.randomUUID();

        subject.redeem(deleted, OrderId.of(UUID.randomUUID()), NOW);

        assertThat(usesOf(deleted)).isZero();
    }
}
