package com.pijamaparty.store.promotions.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CouponDiscount;
import com.pijamaparty.store.promotions.api.CouponStatus;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-10: {@link CouponDiscountsService}, the public API of promotions for the cart. */
class CouponDiscountsServiceTest {

    /** 2026-10-31 at 23:59 in Buenos Aires (UTC−3), already November 1 in UTC. */
    private static final Instant LAST_MINUTE_OF_OCTOBER = Instant.parse("2026-11-01T02:59:00Z");
    private static final Money SUBTOTAL = Money.of(2_000_000);
    private static final CouponId VERANO = CouponId.of(UUID.randomUUID());

    private final InMemoryCouponStore coupons = new InMemoryCouponStore();

    @Test
    void givenAValid10PercentCoupon_whenAskedByItsTypedCode_thenItAppliesWithA2000Discount() {
        coupons.add(coupon(VERANO, "VERANO10", Optional.empty(), Optional.empty(), Optional.empty()));

        assertThat(service(LAST_MINUTE_OF_OCTOBER).byCode("  verano10 ", SUBTOTAL)).contains(new CouponDiscount(
            VERANO.value(), "VERANO10", CouponStatus.APPLICABLE, Money.of(200_000), Money.zero()));
        assertThat(service(LAST_MINUTE_OF_OCTOBER).byId(VERANO.value(), SUBTOTAL)).contains(new CouponDiscount(
            VERANO.value(), "VERANO10", CouponStatus.APPLICABLE, Money.of(200_000), Money.zero()));
    }

    @Test
    void givenAnUnknownOrMalformedCodeOrId_whenAskedForIt_thenThereIsNoCoupon() {
        coupons.add(coupon(VERANO, "VERANO10", Optional.empty(), Optional.empty(), Optional.empty()));

        assertThat(service(LAST_MINUTE_OF_OCTOBER).byCode("INVIERNO10", SUBTOTAL)).isEmpty();
        assertThat(service(LAST_MINUTE_OF_OCTOBER).byCode("verano 10", SUBTOTAL)).isEmpty();
        assertThat(service(LAST_MINUTE_OF_OCTOBER).byCode("", SUBTOTAL)).isEmpty();
        assertThat(service(LAST_MINUTE_OF_OCTOBER).byId(UUID.randomUUID(), SUBTOTAL)).isEmpty();
    }

    @Test
    void givenACouponValidUntilOctober31_whenAskedAroundMidnightInBuenosAires_thenItExpiresOnTheStoreDate() {
        coupons.add(coupon(VERANO, "VERANO10", Optional.of(LocalDate.of(2026, 10, 31)), Optional.empty(),
            Optional.empty()));

        assertThat(service(LAST_MINUTE_OF_OCTOBER).byId(VERANO.value(), SUBTOTAL))
            .map(CouponDiscount::status).contains(CouponStatus.APPLICABLE);
        assertThat(service(LAST_MINUTE_OF_OCTOBER.plusSeconds(60)).byId(VERANO.value(), SUBTOTAL))
            .contains(new CouponDiscount(VERANO.value(), "VERANO10", CouponStatus.EXPIRED, Money.zero(),
                Money.zero()));
    }

    @Test
    void givenACouponWithAllItsUsesTaken_whenAskedForIt_thenItIsUsedUp() {
        coupons.add(coupon(VERANO, "VERANO10", Optional.empty(), Optional.of(2), Optional.empty()));
        coupons.redeem(VERANO, 1);

        assertThat(service(LAST_MINUTE_OF_OCTOBER).byId(VERANO.value(), SUBTOTAL))
            .map(CouponDiscount::status).contains(CouponStatus.APPLICABLE);
        coupons.redeem(VERANO, 1);
        assertThat(service(LAST_MINUTE_OF_OCTOBER).byId(VERANO.value(), SUBTOTAL))
            .map(CouponDiscount::status).contains(CouponStatus.USED_UP);
    }

    @Test
    void givenACartBelowTheMinimum_whenAskedForTheCoupon_thenItTellsHowMuchIsMissing() {
        coupons.add(coupon(VERANO, "VERANO10", Optional.empty(), Optional.empty(), Optional.of(Money.of(3_000_000))));

        assertThat(service(LAST_MINUTE_OF_OCTOBER).byCode("VERANO10", SUBTOTAL)).contains(new CouponDiscount(
            VERANO.value(), "VERANO10", CouponStatus.BELOW_MINIMUM, Money.zero(), Money.of(1_000_000)));
    }

    @Test
    void givenAnInactiveOrNotStartedCoupon_whenAskedForIt_thenItSaysWhy() {
        CouponId inactive = CouponId.of(UUID.randomUUID());
        CouponId november = CouponId.of(UUID.randomUUID());
        coupons.add(Coupon.of(inactive, new CouponCode("OFF"), CouponType.PERCENTAGE, 10, Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), false));
        coupons.add(Coupon.of(november, new CouponCode("NOVIEMBRE"), CouponType.PERCENTAGE, 10,
            Optional.of(LocalDate.of(2026, 11, 1)), Optional.empty(), Optional.empty(), Optional.empty(), true));

        assertThat(service(LAST_MINUTE_OF_OCTOBER).byCode("OFF", SUBTOTAL))
            .map(CouponDiscount::status).contains(CouponStatus.INACTIVE);
        assertThat(service(LAST_MINUTE_OF_OCTOBER).byCode("NOVIEMBRE", SUBTOTAL))
            .map(CouponDiscount::status).contains(CouponStatus.NOT_STARTED);
    }

    private CouponDiscountsService service(Instant now) {
        return new CouponDiscountsService(coupons, coupons, Clock.fixed(now, TimeConfiguration.STORE_ZONE));
    }

    private static Coupon coupon(CouponId id, String code, Optional<LocalDate> validUntil, Optional<Integer> maxUses,
                                 Optional<Money> minimumPurchase) {
        return Coupon.of(id, new CouponCode(code), CouponType.PERCENTAGE, 10, Optional.empty(), validUntil, maxUses,
            minimumPurchase, true);
    }
}
