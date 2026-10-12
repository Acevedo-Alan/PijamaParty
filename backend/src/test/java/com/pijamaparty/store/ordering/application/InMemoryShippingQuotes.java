package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.Province;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Fake of {@link ShippingQuotes}: a cost per province and an optional free-shipping threshold, as UC-12. */
public class InMemoryShippingQuotes implements ShippingQuotes {

    private final Map<Province, Money> costs = new EnumMap<>(Province.class);
    private Optional<Money> freeShippingThreshold = Optional.empty();

    public void rate(Province province, Money cost) {
        costs.put(province, cost);
    }

    public void freeShippingFrom(Money threshold) {
        freeShippingThreshold = Optional.of(threshold);
    }

    @Override
    public Optional<Money> costTo(Province province, Money subtotalAfterDiscount) {
        boolean free = freeShippingThreshold.filter(threshold -> !subtotalAfterDiscount.isLessThan(threshold))
            .isPresent();
        return Optional.ofNullable(costs.get(province)).map(cost -> free ? Money.zero() : cost);
    }
}
