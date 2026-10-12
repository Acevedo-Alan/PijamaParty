package com.pijamaparty.store.promotions.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A code coupon (UC-10, UC-11). It holds the rules of its data: a whole percent from 1 to 100 or an amount of at most
 * {@link #MAX_AMOUNT}, a validity whose start is not after its end, at most {@link #MAX_USES} uses and a minimum
 * purchase of at most {@link #MAX_AMOUNT}, both above 0 when present. It decides whether it applies to a subtotal and
 * how much it discounts. Its uses are counted from the confirmed orders (E3).
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
    private final boolean active;

    private Coupon(CouponId id, CouponCode code, CouponType type, long discountValue, Optional<LocalDate> validFrom,
                   Optional<LocalDate> validUntil, Optional<Integer> maxUses, Optional<Money> minimumPurchase,
                   boolean active) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = Objects.requireNonNull(code, "code");
        this.type = Objects.requireNonNull(type, "type");
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        this.validUntil = Objects.requireNonNull(validUntil, "validUntil");
        this.maxUses = Objects.requireNonNull(maxUses, "maxUses");
        this.minimumPurchase = Objects.requireNonNull(minimumPurchase, "minimumPurchase");
        Set<Field> invalid = invalidFields(type, discountValue, validFrom, validUntil, maxUses, minimumPurchase);
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("invalid coupon fields: " + invalid);
        }
        this.discountValue = discountValue;
        this.active = active;
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
        return new Coupon(id, code, type, discountValue, validFrom, validUntil, maxUses, minimumPurchase, active);
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
        return new Coupon(id, code, type, discountValue, validFrom, validUntil, maxUses, minimumPurchase, newActive);
    }

    /**
     * UC-10: whether the coupon applies to {@code subtotal} on {@code today}, the store date, after {@code uses}
     * confirmed orders. The checks run in a fixed order and the first that fails is the answer.
     */
    public Evaluation evaluate(Money subtotal, LocalDate today, long uses) {
        Objects.requireNonNull(subtotal, "subtotal");
        Objects.requireNonNull(today, "today");
        if (!active) {
            return new Evaluation.Inactive();
        }
        if (validFrom.filter(today::isBefore).isPresent()) {
            return new Evaluation.NotStarted();
        }
        if (validUntil.filter(today::isAfter).isPresent()) {
            return new Evaluation.Expired();
        }
        if (maxUses.filter(max -> uses >= max).isPresent()) {
            return new Evaluation.UsedUp();
        }
        if (minimumPurchase.filter(subtotal::isLessThan).isPresent()) {
            return new Evaluation.BelowMinimum(minimumPurchase.get().minus(subtotal));
        }
        return new Evaluation.Applicable(discountOn(subtotal));
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
