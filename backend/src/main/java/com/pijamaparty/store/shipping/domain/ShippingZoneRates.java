package com.pijamaparty.store.shipping.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The shipping cost of each province (UC-12): at most one per province, each from 0 to {@link #MAX_COST}. A province
 * without a cost has no shipping; a cost of 0 is a free shipping to it.
 */
public final class ShippingZoneRates {

    /** ARS 10,000,000, the price maximum of UC-06. */
    public static final Money MAX_COST = Money.of(1_000_000_000L);

    private final Map<Province, Money> costs;

    private ShippingZoneRates(Map<Province, Money> costs) {
        EnumMap<Province, Money> copy = new EnumMap<>(Province.class);
        costs.forEach((province, cost) -> {
            Objects.requireNonNull(province, "province");
            if (cost.isGreaterThan(MAX_COST)) {
                throw new IllegalArgumentException("shipping cost above the maximum for " + province);
            }
            copy.put(province, cost);
        });
        this.costs = Collections.unmodifiableMap(copy);
    }

    public static ShippingZoneRates of(Map<Province, Money> costs) {
        return new ShippingZoneRates(costs);
    }

    public static ShippingZoneRates none() {
        return new ShippingZoneRates(Map.of());
    }

    /**
     * UC-12: the costs as the administrator typed them, or every province that breaks a rule: a cost outside 0 to
     * {@link #MAX_COST}, or a province typed more than once.
     */
    public static Parsed parse(List<TypedRate> typed) {
        Map<Province, Money> costs = new EnumMap<>(Province.class);
        Set<Province> seen = EnumSet.noneOf(Province.class);
        Set<Province> invalid = EnumSet.noneOf(Province.class);
        for (TypedRate rate : typed) {
            Objects.requireNonNull(rate.province(), "province");
            if (!seen.add(rate.province()) || rate.costCents() < 0 || rate.costCents() > MAX_COST.cents()) {
                invalid.add(rate.province());
            } else {
                costs.put(rate.province(), Money.of(rate.costCents()));
            }
        }
        return invalid.isEmpty() ? new Parsed.Valid(new ShippingZoneRates(costs)) : new Parsed.Invalid(invalid);
    }

    /** Empty: there is no shipping to that province. */
    public Optional<Money> costOf(Province province) {
        return Optional.ofNullable(costs.get(province));
    }

    /** The costs, in the order of {@link Province}. */
    public Map<Province, Money> costs() {
        return costs;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShippingZoneRates rates && costs.equals(rates.costs);
    }

    @Override
    public int hashCode() {
        return costs.hashCode();
    }

    @Override
    public String toString() {
        return "ShippingZoneRates" + costs;
    }

    /** A cost as typed: any whole number of cents, checked by {@link #parse}. */
    public record TypedRate(Province province, long costCents) {}

    public sealed interface Parsed {

        record Valid(ShippingZoneRates rates) implements Parsed {}

        record Invalid(Set<Province> provinces) implements Parsed {

            public Invalid {
                provinces = Collections.unmodifiableSet(EnumSet.copyOf(provinces));
            }
        }
    }
}
