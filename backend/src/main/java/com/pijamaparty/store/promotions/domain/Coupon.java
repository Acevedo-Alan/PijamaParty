package com.pijamaparty.store.promotions.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A code coupon (UC-10, UC-11) or an automatic one (UC-23), fixed when it is created. It holds the rules of its data:
 * a whole percent from 1 to 100 or an amount of at most {@link #MAX_AMOUNT}, a validity whose start is not after its
 * end, at most {@link #MAX_USES} uses and a minimum purchase of at most {@link #MAX_AMOUNT}, both above 0 when present.
 * Only an automatic coupon may have a scope. It decides whether it applies to a cart and how much it discounts. Its
 * uses are counted from the confirmed orders (E3).
 */
public final class Coupon {

    /** The largest fixed amount and minimum purchase: ARS 10,000,000, the price maximum of UC-06. */
    public static final Money MAX_AMOUNT = Money.of(1_000_000_000L);
    public static final int MAX_PERCENT = 100;
    public static final int MAX_USES = 1_000_000;

    private final CouponId id;
    private final CouponCode code;
    private final CouponType type;
    private final long discountValue;
    private final Optional<LocalDate> validFrom;
    private final Optional<LocalDate> validUntil;
    private final Optional<Integer> maxUses;
    private final Optional<Money> minimumPurchase;
    private final boolean automatic;
    private final CouponScope scope;
    private final boolean active;
    private final List<DiscountRule> rules;

    private Coupon(CouponId id, CouponCode code, CouponType type, long discountValue, Optional<LocalDate> validFrom,
                   Optional<LocalDate> validUntil, Optional<Integer> maxUses, Optional<Money> minimumPurchase,
                   boolean automatic, CouponScope scope, boolean active) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = Objects.requireNonNull(code, "code");
        this.type = Objects.requireNonNull(type, "type");
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        this.validUntil = Objects.requireNonNull(validUntil, "validUntil");
        this.maxUses = Objects.requireNonNull(maxUses, "maxUses");
        this.minimumPurchase = Objects.requireNonNull(minimumPurchase, "minimumPurchase");
        this.scope = Objects.requireNonNull(scope, "scope");
        if (!automatic && !scope.isWholeCart()) {
            throw new IllegalArgumentException("only an automatic coupon has a scope");
        }
        Set<Field> invalid = invalidFields(type, discountValue, validFrom, validUntil, maxUses, minimumPurchase);
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("invalid coupon fields: " + invalid);
        }
        this.discountValue = discountValue;
        this.automatic = automatic;
        this.active = active;
        this.rules = rulesOf(validFrom, validUntil, maxUses, scope, minimumPurchase);
    }

    /**
     * @param discountValue the percent for {@link CouponType#PERCENTAGE}, the cents for
     *                      {@link CouponType#FIXED_AMOUNT}
     * @param validFrom the first day it applies, inclusive, in the store's time zone (R-07)
     * @param validUntil the last day it applies, inclusive
     * @param maxUses empty: unlimited
     * @param minimumPurchase empty: no minimum
     */
    public static Coupon of(CouponId id, CouponCode code, CouponType type, long discountValue,
                            Optional<LocalDate> validFrom, Optional<LocalDate> validUntil, Optional<Integer> maxUses,
                            Optional<Money> minimumPurchase, boolean active) {
        return new Coupon(id, code, type, discountValue, validFrom, validUntil, maxUses, minimumPurchase, false,
            CouponScope.WHOLE_CART, active);
    }

    /**
     * UC-23: a coupon that applies on its own when a cart meets its rules; its code is only its label. The other
     * parameters are those of {@link #of}.
     *
     * @param scope the products and categories it discounts, or {@link CouponScope#WHOLE_CART}
     */
    public static Coupon automatic(CouponId id, CouponCode code, CouponType type, long discountValue,
                                   Optional<LocalDate> validFrom, Optional<LocalDate> validUntil,
                                   Optional<Integer> maxUses, Optional<Money> minimumPurchase, CouponScope scope,
                                   boolean active) {
        return new Coupon(id, code, type, discountValue, validFrom, validUntil, maxUses, minimumPurchase, true, scope,
            active);
    }

    /**
     * UC-23: its rules in the order they are checked. The scope comes before the minimum purchase, which is compared
     * with the matching subtotal.
     */
    private static List<DiscountRule> rulesOf(Optional<LocalDate> validFrom, Optional<LocalDate> validUntil,
                                              Optional<Integer> maxUses, CouponScope scope,
                                              Optional<Money> minimumPurchase) {
        List<DiscountRule> rules = new ArrayList<>();
        if (validFrom.isPresent() || validUntil.isPresent()) {
            rules.add(new ValidityRule(validFrom, validUntil));
        }
        maxUses.ifPresent(max -> rules.add(new MaxUsesRule(max)));
        if (!scope.isWholeCart()) {
            rules.add(new ScopeRule(scope));
        }
        minimumPurchase.ifPresent(minimum -> rules.add(new MinimumPurchaseRule(minimum)));
        return List.copyOf(rules);
    }

    /** UC-11: every rule these terms break, as in {@link #of}; empty when they make a valid coupon. */
    public static Set<Field> invalidFields(CouponType type, long discountValue, Optional<LocalDate> validFrom,
                                           Optional<LocalDate> validUntil, Optional<Integer> maxUses,
                                           Optional<Money> minimumPurchase) {
        Objects.requireNonNull(type, "type");
        Set<Field> invalid = EnumSet.noneOf(Field.class);
        long maxValue = type == CouponType.PERCENTAGE ? MAX_PERCENT : MAX_AMOUNT.cents();
        if (discountValue <= 0 || discountValue > maxValue) {
            invalid.add(Field.DISCOUNT_VALUE);
        }
        if (validFrom.isPresent() && validUntil.isPresent() && validFrom.get().isAfter(validUntil.get())) {
            invalid.add(Field.VALIDITY);
        }
        if (maxUses.filter(uses -> uses <= 0 || uses > MAX_USES).isPresent()) {
            invalid.add(Field.MAX_USES);
        }
        if (minimumPurchase.filter(minimum -> minimum.isZero() || minimum.isGreaterThan(MAX_AMOUNT)).isPresent()) {
            invalid.add(Field.MINIMUM_PURCHASE);
        }
        return Set.copyOf(invalid);
    }

    /** UC-11: the same coupon, active, so carts can apply it again. */
    public Coupon activated() {
        return withActive(true);
    }

    /** UC-11: the same coupon, inactive; a cart that holds it gets no discount (UC-10). */
    public Coupon deactivated() {
        return withActive(false);
    }

    private Coupon withActive(boolean newActive) {
        return new Coupon(id, code, type, discountValue, validFrom, validUntil, maxUses, minimumPurchase, automatic,
            scope, newActive);
    }

    /**
     * UC-10: whether a coupon without a scope applies to {@code subtotal} on {@code today}, the store date, after
     * {@code uses} confirmed orders. As {@link #evaluate(DiscountContext)}.
     *
     * @throws IllegalStateException when the coupon has a scope, which needs the cart's lines
     */
    public Evaluation evaluate(Money subtotal, LocalDate today, long uses) {
        return evaluate(DiscountContext.ofSubtotal(subtotal, today, uses));
    }

    /**
     * UC-10, UC-23: whether the coupon applies in {@code context}. It must be active, then its rules are checked in
     * order and the first that fails is the answer. When all pass, it discounts the subtotal they leave: for a scoped
     * coupon, the matching lines'.
     */
    public Evaluation evaluate(DiscountContext context) {
        Objects.requireNonNull(context, "context");
        if (!active) {
            return new Evaluation.Inactive();
        }
        DiscountContext checked = context;
        for (DiscountRule rule : rules) {
            switch (rule.check(checked)) {
                case DiscountRule.Outcome.Failed failed -> {
                    return failed.reason();
                }
                case DiscountRule.Outcome.Passed passed -> checked = passed.context();
            }
        }
        return new Evaluation.Applicable(discountOn(checked.subtotal()));
    }

    /** A percentage rounded down to the cent, so never more than the stated percent; a fixed amount at most all. */
    private Money discountOn(Money subtotal) {
        return switch (type) {
            case PERCENTAGE -> subtotal.percent((int) discountValue);
            case FIXED_AMOUNT -> Money.of(discountValue).min(subtotal);
        };
    }

    public CouponId id() {
        return id;
    }

    public CouponCode code() {
        return code;
    }

    public CouponType type() {
        return type;
    }

    public long discountValue() {
        return discountValue;
    }

    public Optional<LocalDate> validFrom() {
        return validFrom;
    }

    public Optional<LocalDate> validUntil() {
        return validUntil;
    }

    public Optional<Integer> maxUses() {
        return maxUses;
    }

    public Optional<Money> minimumPurchase() {
        return minimumPurchase;
    }

    public boolean automatic() {
        return automatic;
    }

    public CouponScope scope() {
        return scope;
    }

    public boolean active() {
        return active;
    }

    /** A rule of UC-11 that the terms of a coupon can break. */
    public enum Field {
        /** A percent outside 1 to 100, or an amount outside 1 cent to {@link Coupon#MAX_AMOUNT}. */
        DISCOUNT_VALUE,
        /** The validity starts after it ends. */
        VALIDITY,
        /** Outside 1 to {@link Coupon#MAX_USES}. */
        MAX_USES,
        /** Outside 1 cent to {@link Coupon#MAX_AMOUNT}. */
        MINIMUM_PURCHASE
    }

    public sealed interface Evaluation {

        /** The coupon applies and takes {@code discount} off, at most the subtotal. */
        record Applicable(Money discount) implements Evaluation {}

        /** Deactivated by the administrator. */
        record Inactive() implements Evaluation {}

        /** Today is before its first day. */
        record NotStarted() implements Evaluation {}

        /** Today is after its last day ("El cupón venció"). */
        record Expired() implements Evaluation {}

        /** Its maximum uses are taken. */
        record UsedUp() implements Evaluation {}

        /** The subtotal needs {@code missing} more to reach the minimum purchase. */
        record BelowMinimum(Money missing) implements Evaluation {}
    }
}
