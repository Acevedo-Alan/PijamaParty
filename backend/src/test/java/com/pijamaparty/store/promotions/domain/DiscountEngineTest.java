package com.pijamaparty.store.promotions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.domain.Coupon.Evaluation;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-23: the discount rules of an automatic coupon and the choice of the one discount that applies. */
class DiscountEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final UUID PAJAMAS = UUID.randomUUID();
    private static final UUID ROBES = UUID.randomUUID();
    private static final UUID SHORT_PAJAMA = UUID.randomUUID();

    @Test
    void givenARule10PercentFrom30000_whenTheCartExceeds30000_thenTheDiscountAppearsWithoutACode() {
        Coupon tenFrom30000 = automatic("VERANO10", 10, Optional.of(Money.of(3_000_000)), CouponScope.WHOLE_CART);

        var pricing = price(List.of(line(PAJAMAS, 3_500_000)), Optional.empty(), tenFrom30000);

        assertThat(pricing.codeCoupon()).isEmpty();
        assertThat(pricing.applied()).contains(new AppliedDiscount(tenFrom30000, Money.of(350_000)));
    }

    @Test
    void givenACartThatFallsBelowTheMinimum_whenPricedAgain_thenTheDiscountIsWithdrawn() {
        Coupon tenFrom30000 = automatic("VERANO10", 10, Optional.of(Money.of(3_000_000)), CouponScope.WHOLE_CART);

        var pricing = price(List.of(line(PAJAMAS, 2_000_000)), Optional.empty(), tenFrom30000);

        assertThat(pricing.applied()).isEmpty();
    }

    @Test
    void givenTwoApplicableRules_whenPriced_thenTheOneThatDiscountsMoreIsUsed() {
        Coupon ten = automatic("VERANO10", 10, Optional.empty(), CouponScope.WHOLE_CART);
        Coupon fifteen = automatic("VERANO15", 15, Optional.empty(), CouponScope.WHOLE_CART);

        var pricing = price(List.of(line(PAJAMAS, 2_000_000)), Optional.empty(), ten, fifteen);

        assertThat(pricing.applied()).contains(new AppliedDiscount(fifteen, Money.of(300_000)));
    }

    @Test
    void givenTwoAutomaticCouponsWithTheSameDiscount_whenPriced_thenTheOldestWins() {
        Coupon older = automatic("VERANO10", 10, Optional.empty(), CouponScope.WHOLE_CART);
        Coupon newer = automatic("OTONO10", 10, Optional.empty(), CouponScope.WHOLE_CART);

        var pricing = price(List.of(line(PAJAMAS, 2_000_000)), Optional.empty(), older, newer);

        assertThat(pricing.applied()).map(AppliedDiscount::coupon).contains(older);
    }

    @Test
    void givenARuleOutsideItsValidity_whenPriced_thenItDoesNotApply() {
        Coupon expired = Coupon.automatic(CouponId.of(UUID.randomUUID()), new CouponCode("INVIERNO"),
            CouponType.PERCENTAGE, 10, Optional.empty(), Optional.of(TODAY.minusDays(1)), Optional.empty(),
            Optional.empty(), CouponScope.WHOLE_CART, true);
        Coupon notStarted = Coupon.automatic(CouponId.of(UUID.randomUUID()), new CouponCode("PRIMAVERA"),
            CouponType.PERCENTAGE, 10, Optional.of(TODAY.plusDays(1)), Optional.empty(), Optional.empty(),
            Optional.empty(), CouponScope.WHOLE_CART, true);

        var pricing = price(List.of(line(PAJAMAS, 2_000_000)), Optional.empty(), expired, notStarted);

        assertThat(pricing.applied()).isEmpty();
    }

    @Test
    void givenAnInactiveOrUsedUpRule_whenPriced_thenItDoesNotApply() {
        Coupon inactive = automatic("VERANO10", 10, Optional.empty(), CouponScope.WHOLE_CART).deactivated();
        Coupon oneUse = Coupon.automatic(CouponId.of(UUID.randomUUID()), new CouponCode("UNAVEZ"),
            CouponType.PERCENTAGE, 10, Optional.empty(), Optional.empty(), Optional.of(1), Optional.empty(),
            CouponScope.WHOLE_CART, true);

        var pricing = DiscountEngine.price(List.of(line(PAJAMAS, 2_000_000)), TODAY, Optional.empty(),
            List.of(new CountedCoupon(inactive, 0), new CountedCoupon(oneUse, 1)));

        assertThat(pricing.applied()).isEmpty();
    }

    @Test
    void givenACouponScopedToACategory_whenTheCartHoldsItemsOfItAndOthers_thenOnlyItsItemsAreDiscounted() {
        Coupon robes = automatic("BATAS20", 20, Optional.empty(), new CouponScope(Set.of(), Set.of(ROBES)));

        var pricing = price(List.of(line(PAJAMAS, 2_000_000), line(ROBES, 1_500_000), line(ROBES, 500_000)),
            Optional.empty(), robes);

        assertThat(pricing.applied()).contains(new AppliedDiscount(robes, Money.of(400_000)));
    }

    @Test
    void givenACouponScopedToAProduct_whenTheCartHoldsIt_thenOnlyItsLinesAreDiscounted() {
        Coupon shortPajama = automatic("SHORT10", 10, Optional.empty(), new CouponScope(Set.of(SHORT_PAJAMA), Set.of()));

        var pricing = price(List.of(new DiscountLine(SHORT_PAJAMA, PAJAMAS, Money.of(1_000_000)),
            line(PAJAMAS, 3_000_000)), Optional.empty(), shortPajama);

        assertThat(pricing.applied()).contains(new AppliedDiscount(shortPajama, Money.of(100_000)));
    }

    @Test
    void givenAScopedCouponWithAMinimum_whenOnlyTheWholeCartReachesIt_thenItDoesNotApply() {
        Coupon robes = automatic("BATAS20", 20, Optional.of(Money.of(2_000_000)),
            new CouponScope(Set.of(), Set.of(ROBES)));

        var pricing = price(List.of(line(PAJAMAS, 5_000_000), line(ROBES, 1_500_000)), Optional.empty(), robes);

        assertThat(pricing.applied()).isEmpty();
    }

    @Test
    void givenAScopedFixedAmount_whenItExceedsTheMatchingLines_thenItTakesAtMostTheirSubtotal() {
        Coupon robes = Coupon.automatic(CouponId.of(UUID.randomUUID()), new CouponCode("BATAS"),
            CouponType.FIXED_AMOUNT, 1_000_000, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), new CouponScope(Set.of(), Set.of(ROBES)), true);

        var pricing = price(List.of(line(PAJAMAS, 5_000_000), line(ROBES, 600_000)), Optional.empty(), robes);

        assertThat(pricing.applied()).contains(new AppliedDiscount(robes, Money.of(600_000)));
    }

    @Test
    void givenAScopedCoupon_whenNoLineMatches_thenItDoesNotApply() {
        Coupon robes = automatic("BATAS20", 20, Optional.empty(), new CouponScope(Set.of(), Set.of(ROBES)));

        var pricing = price(List.of(line(PAJAMAS, 2_000_000)), Optional.empty(), robes);

        assertThat(pricing.applied()).isEmpty();
    }

    @Test
    void givenAnApplicableAutomaticCouponAndACodeThatDiscountsMore_whenPriced_thenOnlyTheCodeApplies() {
        Coupon automatic = automatic("VERANO10", 10, Optional.empty(), CouponScope.WHOLE_CART);
        Coupon code = codeCoupon("PIJAMA20", 20);

        var pricing = price(List.of(line(PAJAMAS, 2_000_000)), Optional.of(code), automatic);

        assertThat(pricing.codeCoupon()).contains(new Evaluation.Applicable(Money.of(400_000)));
        assertThat(pricing.applied()).contains(new AppliedDiscount(code, Money.of(400_000)));
    }

    @Test
    void givenAnAutomaticCouponAndACodeWithTheSameDiscount_whenPriced_thenTheCodeApplies() {
        Coupon automatic = automatic("VERANO10", 10, Optional.empty(), CouponScope.WHOLE_CART);
        Coupon code = codeCoupon("PIJAMA10", 10);

        var pricing = price(List.of(line(PAJAMAS, 2_000_000)), Optional.of(code), automatic);

        assertThat(pricing.applied()).map(AppliedDiscount::coupon).contains(code);
    }

    @Test
    void givenAnApplicableCodeAndAnAutomaticCouponThatDiscountsMore_whenPriced_thenTheAutomaticOneApplies() {
        Coupon automatic = automatic("VERANO20", 20, Optional.empty(), CouponScope.WHOLE_CART);
        Coupon code = codeCoupon("PIJAMA10", 10);

        var pricing = price(List.of(line(PAJAMAS, 2_000_000)), Optional.of(code), automatic);

        assertThat(pricing.codeCoupon()).contains(new Evaluation.Applicable(Money.of(200_000)));
        assertThat(pricing.applied()).contains(new AppliedDiscount(automatic, Money.of(400_000)));
    }

    @Test
    void givenACodeThatDoesNotApply_whenPriced_thenItsReasonIsKeptAndAnAutomaticCouponCanApply() {
        Coupon automatic = automatic("VERANO10", 10, Optional.empty(), CouponScope.WHOLE_CART);
        Coupon code = Coupon.of(CouponId.of(UUID.randomUUID()), new CouponCode("PIJAMA10"), CouponType.PERCENTAGE, 10,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(Money.of(3_000_000)), true);

        var pricing = price(List.of(line(PAJAMAS, 2_000_000)), Optional.of(code), automatic);

        assertThat(pricing.codeCoupon()).contains(new Evaluation.BelowMinimum(Money.of(1_000_000)));
        assertThat(pricing.applied()).map(AppliedDiscount::coupon).contains(automatic);
    }

    @Test
    void givenNoCoupons_whenPriced_thenNothingApplies() {
        var pricing = price(List.of(line(PAJAMAS, 2_000_000)), Optional.empty());

        assertThat(pricing.codeCoupon()).isEmpty();
        assertThat(pricing.applied()).isEmpty();
    }

    @Test
    void givenCouponsOfTheWrongKind_whenPriced_thenTheyAreRefused() {
        Coupon automatic = automatic("VERANO10", 10, Optional.empty(), CouponScope.WHOLE_CART);
        Coupon code = codeCoupon("PIJAMA10", 10);
        List<DiscountLine> lines = List.of(line(PAJAMAS, 2_000_000));

        assertThatThrownBy(() -> price(lines, Optional.of(automatic)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price(lines, Optional.empty(), code))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAScopeOfMoreThan100Targets_whenBuilt_thenItIsRefused() {
        Set<UUID> hundredProducts = new HashSet<>();
        for (int i = 0; i < CouponScope.MAX_TARGETS; i++) {
            hundredProducts.add(UUID.randomUUID());
        }

        assertThat(new CouponScope(hundredProducts, Set.of()).isWholeCart()).isFalse();
        assertThatThrownBy(() -> new CouponScope(hundredProducts, Set.of(ROBES)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAnAutomaticCoupon_whenDeactivated_thenItKeepsItsKindAndScope() {
        CouponScope robes = new CouponScope(Set.of(), Set.of(ROBES));

        Coupon deactivated = automatic("BATAS20", 20, Optional.empty(), robes).deactivated();

        assertThat(deactivated.automatic()).isTrue();
        assertThat(deactivated.scope()).isEqualTo(robes);
        assertThat(codeCoupon("PIJAMA10", 10).automatic()).isFalse();
        assertThat(codeCoupon("PIJAMA10", 10).scope().isWholeCart()).isTrue();
    }

    @Test
    void givenAScopedCoupon_whenEvaluatedOnASubtotalWithoutLines_thenItIsRefused() {
        Coupon robes = automatic("BATAS20", 20, Optional.empty(), new CouponScope(Set.of(), Set.of(ROBES)));

        assertThatThrownBy(() -> robes.evaluate(Money.of(1_000_000), TODAY, 0))
            .isInstanceOf(IllegalStateException.class);
    }

    private static DiscountEngine.Pricing price(List<DiscountLine> lines, Optional<Coupon> code,
                                                Coupon... automaticOldestFirst) {
        return DiscountEngine.price(lines, TODAY, code.map(coupon -> new CountedCoupon(coupon, 0)),
            Arrays.stream(automaticOldestFirst).map(coupon -> new CountedCoupon(coupon, 0)).toList());
    }

    private static DiscountLine line(UUID categoryId, long cents) {
        return new DiscountLine(UUID.randomUUID(), categoryId, Money.of(cents));
    }

    private static Coupon automatic(String code, int percent, Optional<Money> minimum, CouponScope scope) {
        return Coupon.automatic(CouponId.of(UUID.randomUUID()), new CouponCode(code), CouponType.PERCENTAGE, percent,
            Optional.empty(), Optional.empty(), Optional.empty(), minimum, scope, true);
    }

    private static Coupon codeCoupon(String code, int percent) {
        return Coupon.of(CouponId.of(UUID.randomUUID()), new CouponCode(code), CouponType.PERCENTAGE, percent,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true);
    }
}
