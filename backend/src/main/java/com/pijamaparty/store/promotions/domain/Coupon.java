package com.pijamaparty.store.promotions.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A code coupon (UC-10, UC-11). It holds the rules of its data: a whole percent from 1 to 100 or an amount above 0,
 * a validity whose start is not after its end, and maximum uses and minimum purchase above 0 when present. It decides
 * whether it applies to a subtotal and how much it discounts. Its uses are counted from the confirmed orders (E3).
 */
public final class Coupon {

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
        if (discountValue <= 0 || type == CouponType.PERCENTAGE && discountValue > 100) {
            throw new IllegalArgumentException("discount value out of range: " + discountValue);
        }
        if (validFrom.isPresent() && validUntil.isPresent() && validFrom.get().isAfter(validUntil.get())) {
            throw new IllegalArgumentException("the validity starts after it ends");
        }
        if (maxUses.filter(uses -> uses <= 0).isPresent() || minimumPurchase.filter(Money::isZero).isPresent()) {
            throw new IllegalArgumentException("maximum uses and minimum purchase are above 0 when present");
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
