package com.pijamaparty.store.shipping.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * The shop's shipping settings (UC-12): shipping is free when the subtotal after the discount reaches the optional
 * threshold, from 1 cent to {@link #MAX_THRESHOLD}.
 *
 * @param freeShippingThreshold empty: no free shipping
 */
public record ShippingSettings(Optional<Money> freeShippingThreshold) {

    /** ARS 10,000,000, the price maximum of UC-06. */
    public static final Money MAX_THRESHOLD = Money.of(1_000_000_000L);

    public ShippingSettings {
        Objects.requireNonNull(freeShippingThreshold, "freeShippingThreshold");
        if (freeShippingThreshold.filter(ShippingSettings::outOfRange).isPresent()) {
            throw new IllegalArgumentException("free-shipping threshold out of range: " + freeShippingThreshold.get());
        }
    }

    public static ShippingSettings withoutFreeShipping() {
        return new ShippingSettings(Optional.empty());
    }

    /** UC-12: the settings with the threshold as typed in cents, or empty when it is outside its range. */
    public static Optional<ShippingSettings> withThreshold(Optional<Long> thresholdCents) {
        if (thresholdCents.filter(cents -> cents < 1 || cents > MAX_THRESHOLD.cents()).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new ShippingSettings(thresholdCents.map(Money::of)));
    }

    /**
     * UC-12: what a shipping of {@code cost} charges for a subtotal after the discount: nothing once it reaches the
     * threshold, {@code cost} otherwise.
     */
    public Money chargeFor(Money cost, Money subtotalAfterDiscount) {
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(subtotalAfterDiscount, "subtotalAfterDiscount");
        boolean free = freeShippingThreshold.filter(threshold -> !subtotalAfterDiscount.isLessThan(threshold))
            .isPresent();
        return free ? Money.zero() : cost;
    }

    private static boolean outOfRange(Money threshold) {
        return threshold.isZero() || threshold.isGreaterThan(MAX_THRESHOLD);
    }
}
