package com.pijamaparty.store.promotions.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CartDiscount;
import com.pijamaparty.store.promotions.api.CartDiscounts;
import com.pijamaparty.store.promotions.api.CouponDiscount;
import com.pijamaparty.store.promotions.api.CouponStatus;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponScope;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-10, UC-23: {@link CartDiscountsService}, the discount of a cart through the public API of promotions. */
class CartDiscountsServiceTest {

    /** 2026-10-31 at 23:59 in Buenos Aires (UTC−3), already November 1 in UTC. */
    private static final Instant LAST_MINUTE_OF_OCTOBER = Instant.parse("2026-11-01T02:59:00Z");
    private static final Instant CREATED = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID PAJAMAS = UUID.randomUUID();
    private static final UUID ROBES = UUID.randomUUID();

    private final InMemoryCouponStore coupons = new InMemoryCouponStore();
    private final CartDiscountsService service = new CartDiscountsService(coupons, coupons, coupons,
        Clock.fixed(LAST_MINUTE_OF_OCTOBER, TimeConfiguration.STORE_ZONE));

    @Test
    void givenARule10PercentFrom30000_whenTheCartExceeds30000_thenTheDiscountAppliesWithoutACode() {
        CouponId verano = automatic("VERANO10", 10, Optional.of(Money.of(3_000_000)), Optional.empty(),
            Optional.empty(), CouponScope.WHOLE_CART);

        CartDiscount discount = service.price(List.of(line(PAJAMAS, 3_500_000)), Optional.empty());

        assertThat(discount).isEqualTo(new CartDiscount(Optional.empty(),
            Optional.of(new CartDiscount.Applied(verano.value(), "VERANO10", true, Money.of(350_000)))));
        assertThat(service.price(List.of(line(PAJAMAS, 2_000_000)), Optional.empty()).applied()).isEmpty();
    }

    @Test
    void givenARuleOutsideItsValidityPeriodInTheStoreDate_whenTheCartIsPriced_thenItDoesNotApply() {
        automatic("SEPTIEMBRE", 10, Optional.empty(), Optional.empty(), Optional.of(LocalDate.of(2026, 9, 30)),
            CouponScope.WHOLE_CART);
        automatic("NOVIEMBRE", 10, Optional.empty(), Optional.of(LocalDate.of(2026, 11, 1)), Optional.empty(),
            CouponScope.WHOLE_CART);

        assertThat(service.price(List.of(line(PAJAMAS, 3_500_000)), Optional.empty()).applied()).isEmpty();
    }

    @Test
    void givenACouponScopedToACategory_whenTheCartHoldsItemsOfItAndOthers_thenOnlyItsItemsAreDiscounted() {
        UUID robesCategory = UUID.randomUUID();
        automatic("BATAS20", 20, Optional.empty(), Optional.empty(), Optional.empty(),
            new CouponScope(Set.of(), Set.of(robesCategory)));

        CartDiscount discount = service.price(List.of(line(PAJAMAS, 3_000_000),
            new CartDiscounts.Line(ROBES, robesCategory, Money.of(1_000_000))), Optional.empty());

        assertThat(discount.amount()).isEqualTo(Money.of(200_000));
    }

    @Test
    void givenAnAutomaticCouponAndATypedCodeThatDiscountsMore_whenPriced_thenOnlyTheCodeApplies() {
        automatic("VERANO10", 10);
        CouponId code = code("PIJAMA15", 15);

        CartDiscount discount = service.price(List.of(line(PAJAMAS, 2_000_000)), Optional.of(code.value()));

        assertThat(discount).isEqualTo(new CartDiscount(
            Optional.of(new CouponDiscount(code.value(), "PIJAMA15", CouponStatus.APPLICABLE, Money.of(300_000),
                Money.zero())),
            Optional.of(new CartDiscount.Applied(code.value(), "PIJAMA15", false, Money.of(300_000)))));
    }

    @Test
    void givenAnAutomaticCouponAndATypedCodeWithTheSameDiscount_whenPriced_thenTheCodeApplies() {
        automatic("VERANO10", 10);
        CouponId code = code("PIJAMA10", 10);

        CartDiscount discount = service.price(List.of(line(PAJAMAS, 2_000_000)), Optional.of(code.value()));

        assertThat(discount.applied()).map(CartDiscount.Applied::code).contains("PIJAMA10");
    }

    @Test
    void givenAnApplicableCodeAndAnAutomaticCouponThatDiscountsMore_whenPriced_thenTheCodeStaysApplicableButIsNotAdded() {
        CouponId verano = automatic("VERANO20", 20);
        CouponId code = code("PIJAMA10", 10);

        CartDiscount discount = service.price(List.of(line(PAJAMAS, 2_000_000)), Optional.of(code.value()));

        assertThat(discount.coupon()).map(CouponDiscount::status).contains(CouponStatus.APPLICABLE);
        assertThat(discount.applied())
            .contains(new CartDiscount.Applied(verano.value(), "VERANO20", true, Money.of(400_000)));
    }

    @Test
    void givenAUsedUpOrInactiveAutomaticCoupon_whenTheCartIsPriced_thenItDoesNotApply() {
        CouponId usedUp = CouponId.of(UUID.randomUUID());
        coupons.save(Coupon.automatic(usedUp, new CouponCode("AGOTADO"), CouponType.PERCENTAGE, 10, Optional.empty(),
            Optional.empty(), Optional.of(1), Optional.empty(), CouponScope.WHOLE_CART, true), CREATED);
        coupons.redeem(usedUp, 1);
        coupons.save(Coupon.automatic(CouponId.of(UUID.randomUUID()), new CouponCode("APAGADO"), CouponType.PERCENTAGE,
            10, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), CouponScope.WHOLE_CART, false),
            CREATED);

        assertThat(service.price(List.of(line(PAJAMAS, 2_000_000)), Optional.empty()).applied()).isEmpty();
    }

    @Test
    void givenAHeldCouponThatNoLongerExistsOrIsAutomatic_whenPriced_thenTheCartHoldsNone() {
        CouponId verano = automatic("VERANO10", 10);

        CartDiscount deleted = service.price(List.of(line(PAJAMAS, 2_000_000)), Optional.of(UUID.randomUUID()));
        CartDiscount automatic = service.price(List.of(line(PAJAMAS, 2_000_000)), Optional.of(verano.value()));

        assertThat(deleted.coupon()).isEmpty();
        assertThat(automatic.coupon()).isEmpty();
        assertThat(automatic.applied()).map(CartDiscount.Applied::code).contains("VERANO10");
    }

    @Test
    void givenAHeldCodeCouponThatDoesNotApply_whenPriced_thenItSaysWhyAndAnAutomaticCouponStillApplies() {
        automatic("VERANO10", 10);
        CouponId code = CouponId.of(UUID.randomUUID());
        coupons.save(Coupon.of(code, new CouponCode("MINIMO"), CouponType.PERCENTAGE, 20, Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.of(Money.of(3_000_000)), true), CREATED);

        CartDiscount discount = service.price(List.of(line(PAJAMAS, 2_000_000)), Optional.of(code.value()));

        assertThat(discount.coupon()).contains(new CouponDiscount(code.value(), "MINIMO", CouponStatus.BELOW_MINIMUM,
            Money.zero(), Money.of(1_000_000)));
        assertThat(discount.applied()).map(CartDiscount.Applied::code).contains("VERANO10");
    }

    /** Without validity limits, uses limit, minimum purchase or scope. */
    private CouponId automatic(String code, int percent) {
        return automatic(code, percent, Optional.empty(), Optional.empty(), Optional.empty(), CouponScope.WHOLE_CART);
    }

    private CouponId automatic(String code, int percent, Optional<Money> minimum, Optional<LocalDate> validFrom,
                               Optional<LocalDate> validUntil, CouponScope scope) {
        CouponId id = CouponId.of(UUID.randomUUID());
        coupons.save(Coupon.automatic(id, new CouponCode(code), CouponType.PERCENTAGE, percent, validFrom, validUntil,
            Optional.empty(), minimum, scope, true), CREATED);
        return id;
    }

    private CouponId code(String code, int percent) {
        CouponId id = CouponId.of(UUID.randomUUID());
        coupons.save(Coupon.of(id, new CouponCode(code), CouponType.PERCENTAGE, percent, Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), true), CREATED);
        return id;
    }

    private static CartDiscounts.Line line(UUID product, long cents) {
        return new CartDiscounts.Line(product, UUID.randomUUID(), Money.of(cents));
    }
}
