package com.pijamaparty.store.platform.domain;

/**
 * An amount in Argentine pesos (ARS, the only currency of the store), stored as integer cents (R-01).
 * Never negative; arithmetic is exact and fails on overflow. No floating point anywhere.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final String CURRENCY = "ARS";

    private static final Money ZERO = new Money(0);

    public Money {
        if (cents < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + cents + " cents");
        }
    }

    public static Money of(long cents) {
        return new Money(cents);
    }

    public static Money zero() {
        return ZERO;
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    /** Fails when the result would be negative; callers that cap at zero must check {@link #isLessThan} first. */
    public Money minus(Money other) {
        return new Money(Math.subtractExact(cents, other.cents));
    }

    public Money times(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative: " + quantity);
        }
        return new Money(Math.multiplyExact(cents, quantity));
    }

    /** {@code percent} percent of this amount, rounded down to the cent (UC-10); {@code percent} from 0 to 100. */
    public Money percent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Percent out of range: " + percent);
        }
        return new Money(Math.multiplyExact(cents, percent) / 100);
    }

    public Money min(Money other) {
        return isGreaterThan(other) ? other : this;
    }

    public boolean isZero() {
        return cents == 0;
    }

    public boolean isGreaterThan(Money other) {
        return cents > other.cents;
    }

    public boolean isLessThan(Money other) {
        return cents < other.cents;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }

    @Override
    public String toString() {
        return CURRENCY + " " + (cents / 100) + "." + String.format("%02d", cents % 100);
    }
}
